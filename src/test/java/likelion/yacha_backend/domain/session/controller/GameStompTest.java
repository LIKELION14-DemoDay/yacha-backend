package likelion.yacha_backend.domain.session.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.service.GameMemoService;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spectate.enabled=true")
@Import(SessionFixture.class)
@DisplayName("토론방 STOMP — 채팅 · 주장 제출 알림 · 공개 · 관전 구독")
class GameStompTest {

    private static final long TIMEOUT_SECONDS = 5;
    /** 받지 말아야 할 메시지를 기다리는 시간 */
    private static final long SILENCE_MILLIS = 500;

    private static final long IN_PREP = 10;
    private static final long IN_REBUTTAL = 90;
    /** 반론 공개(143초) 뒤 채팅 */
    private static final long IN_CHAT = 150;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private GameMemoService gameMemoService;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler clientScheduler;
    private final List<Room> rooms = new ArrayList<>();

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        clientScheduler = new ThreadPoolTaskScheduler();
        clientScheduler.initialize();
        stompClient.setTaskScheduler(clientScheduler);
        stompClient.setDefaultHeartbeat(new long[]{0, 0});
    }

    @AfterEach
    void tearDown() {
        rooms.forEach(fixture::removeGame);
        stompClient.stop();
        clientScheduler.shutdown();
    }

    private Room track(Room room) {
        rooms.add(room);
        return room;
    }

    @Nested
    @DisplayName("채팅")
    class Chat {

        @Test
        @DisplayName("참가자가 보낸 채팅을 참가자 · 관전자 모두 seqNo · 참가자 id · +09:00 시각과 함께 받는다")
        void broadcastsToParticipantsAndSpectators() throws Exception {
            Room room = track(fixture.randomHuman(IN_CHAT));
            Client host = connect(room.hostUserId());
            Client opponent = connect(room.opponentUserId());
            Client spectator = connect(fixture.newUser());
            BlockingQueue<Map<String, Object>> toOpponent = opponent.subscribeTopic(room.sessionId());
            BlockingQueue<Map<String, Object>> toSpectator = spectator.subscribeTopic(room.sessionId());

            host.send("/app/sessions/" + room.sessionId() + "/chat", Map.of("content", "첫 주장"));

            for (BlockingQueue<Map<String, Object>> inbox : List.of(toOpponent, toSpectator)) {
                Map<String, Object> event = next(inbox, TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                assertThat(event).isNotNull();
                assertThat(event).containsEntry("type", "CHAT")
                        .containsEntry("phase", "CHAT")
                        .containsEntry("content", "첫 주장");
                assertThat(((Number) event.get("seqNo")).longValue()).isEqualTo(1);
                assertThat(((Number) event.get("senderId")).longValue()).isEqualTo(room.hostParticipantId());
                assertThat((String) event.get("receivedAt")).endsWith("+09:00");
                assertThat(event).doesNotContainKey("userId");
            }
            assertThat(spectator.errorFrame).isNotDone();
        }

        @Test
        @DisplayName("첫 채팅 전에 양쪽 반론이 ARGUMENT 로 공개돼 참가자 · 관전자 모두 seqNo 순서대로 받는다")
        void revealsArgumentsBeforeFirstChat() throws Exception {
            Room room = track(fixture.randomHuman(IN_CHAT));
            Game game = gameRegistry.find(room.sessionId()).orElseThrow();
            LocalDateTime inRebuttal = game.getStartedAt().plusSeconds(IN_REBUTTAL);
            game.saveMemo(room.hostUserId(), "방장 주장", inRebuttal);
            game.saveMemo(room.opponentUserId(), "상대 주장", inRebuttal);
            Client host = connect(room.hostUserId());
            Client opponent = connect(room.opponentUserId());
            Client spectator = connect(fixture.newUser());
            BlockingQueue<Map<String, Object>> toHost = host.subscribeTopic(room.sessionId());
            BlockingQueue<Map<String, Object>> toSpectator = spectator.subscribeTopic(room.sessionId());

            opponent.send("/app/sessions/" + room.sessionId() + "/chat", Map.of("content", "첫 채팅"));

            for (BlockingQueue<Map<String, Object>> inbox : List.of(toHost, toSpectator)) {
                long timeout = TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
                Map<String, Object> first = next(inbox, timeout);
                Map<String, Object> second = next(inbox, timeout);
                Map<String, Object> third = next(inbox, timeout);
                assertThat(first).containsEntry("type", "ARGUMENT").containsEntry("phase", "REBUTTAL")
                        .containsEntry("content", "방장 주장");
                assertThat(((Number) first.get("senderId")).longValue()).isEqualTo(room.hostParticipantId());
                assertThat(second).containsEntry("type", "ARGUMENT").containsEntry("content", "상대 주장");
                assertThat(third).containsEntry("type", "CHAT").containsEntry("content", "첫 채팅");
                assertThat(List.of(first, second, third))
                        .extracting(event -> ((Number) event.get("seqNo")).longValue())
                        .containsExactly(1L, 2L, 3L);
            }
        }

        @Test
        @DisplayName("관전자가 보내면 /user/queue/errors 로 NOT_PARTICIPANT 를 받고 연결은 유지된다")
        void spectatorCannotSend() throws Exception {
            Room room = track(fixture.randomHuman(IN_CHAT));
            Client spectator = connect(fixture.newUser());
            BlockingQueue<Map<String, Object>> topic = spectator.subscribeTopic(room.sessionId());
            BlockingQueue<Map<String, Object>> errors = spectator.subscribeErrors();

            spectator.send("/app/sessions/" + room.sessionId() + "/chat", Map.of("content", "끼어들기"));

            assertErrorCode(errors, "NOT_PARTICIPANT");
            assertThat(next(topic, SILENCE_MILLIS)).isNull();
            assertThat(spectator.session.isConnected()).isTrue();
            assertThat(spectator.errorFrame).isNotDone();
        }

        @Test
        @DisplayName("채팅 구간이 아니면 INVALID_PHASE")
        void invalidPhase() throws Exception {
            Room room = track(fixture.randomHuman(IN_PREP));
            Client host = connect(room.hostUserId());
            BlockingQueue<Map<String, Object>> errors = host.subscribeErrors();

            host.send("/app/sessions/" + room.sessionId() + "/chat", Map.of("content", "너무 이름"));

            assertErrorCode(errors, "INVALID_PHASE");
        }

        @Test
        @DisplayName("본문이 비어 있으면 VALIDATION_FAILED")
        void blankContent() throws Exception {
            Room room = track(fixture.randomHuman(IN_CHAT));
            Client host = connect(room.hostUserId());
            BlockingQueue<Map<String, Object>> errors = host.subscribeErrors();

            host.send("/app/sessions/" + room.sessionId() + "/chat", Map.of("content", "   "));

            assertErrorCode(errors, "VALIDATION_FAILED");
        }

        @Test
        @DisplayName("게임이 없는 세션으로 보내면 SESSION_NOT_IN_PROGRESS")
        void noGame() throws Exception {
            Client user = connect(fixture.newUser());
            BlockingQueue<Map<String, Object>> errors = user.subscribeErrors();

            user.send("/app/sessions/999999/chat", Map.of("content", "누구 없나요"));

            assertErrorCode(errors, "SESSION_NOT_IN_PROGRESS");
        }
    }

    @Nested
    @DisplayName("주장 제출 알림")
    class Submitted {

        @Test
        @DisplayName("제출하면 참가자 · 관전자 모두 ARGUMENT_SUBMITTED 를 받고, 내용은 들어 있지 않다")
        void broadcastsWithoutContent() throws Exception {
            Room room = track(fixture.randomHuman(IN_PREP));
            Client opponent = connect(room.opponentUserId());
            Client spectator = connect(fixture.newUser());
            BlockingQueue<Map<String, Object>> toOpponent = opponent.subscribeTopic(room.sessionId());
            BlockingQueue<Map<String, Object>> toSpectator = spectator.subscribeTopic(room.sessionId());

            gameMemoService.saveMemo(room.sessionId(), room.hostUserId(), "비밀 주장");

            for (BlockingQueue<Map<String, Object>> inbox : List.of(toOpponent, toSpectator)) {
                Map<String, Object> event = next(inbox, TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                assertThat(event).isNotNull()
                        .containsEntry("type", "ARGUMENT_SUBMITTED")
                        .containsEntry("phase", "PREP")
                        .doesNotContainKey("content");
                assertThat(((Number) event.get("senderId")).longValue()).isEqualTo(room.hostParticipantId());
                assertThat(event.toString()).doesNotContain("비밀 주장");
            }
        }
    }

    @Nested
    @DisplayName("토론방 구독 — 관전은 진행 중인 랜덤 사람전만")
    class Subscribe {

        @Test
        @DisplayName("친구 방은 참가자만 구독하고 관전자는 FORBIDDEN")
        void friendRoom() throws Exception {
            Room room = track(fixture.friend(IN_CHAT));

            connect(room.opponentUserId()).subscribeTopic(room.sessionId());
            assertSubscribeForbidden(fixture.newUser(), room.sessionId());
        }

        @Test
        @DisplayName("대기 중 AI 로 바뀐 봇전은 RANDOM 방이어도 관전자가 FORBIDDEN")
        void convertedBot() throws Exception {
            Room room = track(fixture.convertedBot(IN_CHAT));

            connect(room.hostUserId()).subscribeTopic(room.sessionId());
            assertSubscribeForbidden(fixture.newUser(), room.sessionId());
        }

        @Test
        @DisplayName("자동 봇전은 RANDOM 방이어도 관전자가 FORBIDDEN")
        void autoBot() throws Exception {
            Room room = track(fixture.autoBot(IN_CHAT));

            connect(room.hostUserId()).subscribeTopic(room.sessionId());
            assertSubscribeForbidden(fixture.newUser(), room.sessionId());
        }

        @Test
        @DisplayName("대기 중인 방은 관전자가 FORBIDDEN")
        void waitingRoom() throws Exception {
            Room room = track(fixture.waitingRandom());

            assertSubscribeForbidden(fixture.newUser(), room.sessionId());
        }

        @Test
        @DisplayName("형식이 맞지 않는 토론방 목적지는 FORBIDDEN")
        void malformedDestination() throws Exception {
            Client user = connect(fixture.newUser());

            user.session.subscribe("/topic/sessions/abc", new QueueFrameHandler(new LinkedBlockingQueue<>()));

            assertThat(user.errorFrame.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");
        }
    }

    /** 비회원은 관전 · 게임만 할 수 있음 (10/2 회의). STOMP 는 역할을 보지 않으므로 GUEST 토큰으로도 그대로 동작해야 합니다. */
    @Nested
    @DisplayName("비회원(GUEST 토큰)")
    class Guest {

        @Test
        @DisplayName("게스트 참가자가 연결 · 구독 · 채팅하고, 게스트 관전자도 받는다")
        void guestCanPlayAndSpectate() throws Exception {
            Room room = track(fixture.randomHuman(IN_CHAT));
            Client host = connect(room.hostUserId(), Role.GUEST);
            Client opponent = connect(room.opponentUserId(), Role.GUEST);
            Client spectator = connect(fixture.newUser(), Role.GUEST);
            BlockingQueue<Map<String, Object>> toOpponent = opponent.subscribeTopic(room.sessionId());
            BlockingQueue<Map<String, Object>> toSpectator = spectator.subscribeTopic(room.sessionId());

            host.send("/app/sessions/" + room.sessionId() + "/chat", Map.of("content", "게스트의 주장"));

            for (BlockingQueue<Map<String, Object>> inbox : List.of(toOpponent, toSpectator)) {
                Map<String, Object> event = next(inbox, TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                assertThat(event).isNotNull();
                assertThat(event).containsEntry("type", "CHAT").containsEntry("content", "게스트의 주장");
            }
            assertThat(host.errorFrame).isNotDone();
            assertThat(spectator.errorFrame).isNotDone();
        }
    }

    // ------------------------------------------------------------------

    /**
     * 다음 메시지. 다른 연결이 구독을 확인하려고 보낸 탐침은 같은 토론방 구독자 모두에게 가므로 건너뜁니다.
     * 기다리는 동안 받은 게 없으면 null.
     */
    private static Map<String, Object> next(BlockingQueue<Map<String, Object>> inbox, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        long remaining;
        while ((remaining = deadline - System.currentTimeMillis()) > 0) {
            Map<String, Object> message = inbox.poll(remaining, TimeUnit.MILLISECONDS);
            if (message != null && !message.containsKey("probe")) {
                return message;
            }
        }
        return null;
    }

    private void assertSubscribeForbidden(Long userId, Long sessionId) throws Exception {
        Client spectator = connect(userId);
        spectator.session.subscribe("/topic/sessions/" + sessionId, new QueueFrameHandler(new LinkedBlockingQueue<>()));
        assertThat(spectator.errorFrame.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");
    }

    @SuppressWarnings("unchecked")
    private static void assertErrorCode(BlockingQueue<Map<String, Object>> errors, String code) throws Exception {
        Map<String, Object> response = next(errors, TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertThat(response).isNotNull().containsEntry("success", false);
        assertThat((Map<String, Object>) response.get("error")).containsEntry("code", code);
    }

    private Client connect(Long userId) throws Exception {
        return connect(userId, Role.USER);
    }

    private Client connect(Long userId, Role role) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(userId, role));
        ErrorFrameHandler handler = new ErrorFrameHandler();
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers, handler)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return new Client(userId, session, handler.errors);
    }

    /** 연결 하나. 구독은 비동기로 등록되므로, 서버에서 탐침 메시지를 보내 받을 때까지 기다린 뒤 돌려줍니다. */
    private class Client {

        private final Long userId;
        private final StompSession session;
        private final CompletableFuture<String> errorFrame;

        Client(Long userId, StompSession session, CompletableFuture<String> errorFrame) {
            this.userId = userId;
            this.session = session;
            this.errorFrame = errorFrame;
        }

        BlockingQueue<Map<String, Object>> subscribeTopic(Long sessionId) throws Exception {
            String destination = "/topic/sessions/" + sessionId;
            return subscribe(destination, () -> messagingTemplate.convertAndSend(destination, probe()));
        }

        BlockingQueue<Map<String, Object>> subscribeErrors() throws Exception {
            return subscribe("/user/queue/errors",
                    () -> messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/errors", probe()));
        }

        void send(String destination, Object payload) {
            session.send(destination, payload);
        }

        private BlockingQueue<Map<String, Object>> subscribe(String destination, Runnable sendProbe) throws Exception {
            BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
            session.subscribe(destination, new QueueFrameHandler(inbox));
            for (int i = 0; i < 50; i++) {
                sendProbe.run();
                Map<String, Object> received = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (received != null && received.containsKey("probe")) {
                    // 늦게 도착한 탐침까지 비웁니다.
                    Thread.sleep(100);
                    inbox.removeIf(message -> message.containsKey("probe"));
                    return inbox;
                }
                assertThat(errorFrame).as("구독이 거부됨: " + destination).isNotDone();
            }
            throw new AssertionError("구독이 등록되지 않았습니다: " + destination);
        }

        /** {@code Map} 으로 넘기면 헤더를 받는 오버로드와 겹쳐서 {@code Object} 로 넘깁니다. */
        private Object probe() {
            return Map.of("probe", true);
        }
    }

    private static class QueueFrameHandler implements StompFrameHandler {

        private final BlockingQueue<Map<String, Object>> inbox;

        QueueFrameHandler(BlockingQueue<Map<String, Object>> inbox) {
            this.inbox = inbox;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            inbox.add((Map<String, Object>) payload);
        }
    }

    /** 서버가 보낸 ERROR 프레임의 message 헤더(에러 코드)를 잡아 둡니다. */
    private static class ErrorFrameHandler extends StompSessionHandlerAdapter {

        private final CompletableFuture<String> errors = new CompletableFuture<>();

        /** ERROR 프레임 본문은 JSON 이 아니므로 변환하지 않고 받습니다. */
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.complete(headers.getFirst("message"));
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                    byte[] payload, Throwable exception) {
            errors.completeExceptionally(exception);
        }
    }
}
