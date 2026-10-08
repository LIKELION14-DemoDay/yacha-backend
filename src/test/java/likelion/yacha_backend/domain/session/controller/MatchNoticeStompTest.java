package likelion.yacha_backend.domain.session.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.service.MatchNotifier;
import likelion.yacha_backend.domain.session.service.SessionMatchFacade;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SessionFixture.class)
@DisplayName("방장 알림 STOMP — MATCHED · WAIT_PROMPT · WAIT_EXPIRED")
class MatchNoticeStompTest {

    private static final long TIMEOUT_SECONDS = 5;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private SessionMatchFacade sessionMatchFacade;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private MatchNotifier matchNotifier;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler clientScheduler;
    private final List<Long> sessionIds = new ArrayList<>();

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
        sessionIds.forEach(gameRegistry::remove);
        stompClient.stop();
        clientScheduler.shutdown();
    }

    @Test
    @DisplayName("상대가 들어오면 방장만 /user/queue/match 로 MATCHED 를 받는다")
    void hostReceivesMatched() throws Exception {
        Room room = fixture.waitingRandom();
        sessionIds.add(room.sessionId());
        Long joinerId = fixture.newUser();
        BlockingQueue<Map<String, Object>> toHost = subscribeMatch(room.hostUserId());
        BlockingQueue<Map<String, Object>> toJoiner = subscribeMatch(joinerId);

        sessionMatchFacade.join(joinerId, room.sessionId());

        Map<String, Object> notice = toHost.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notice).containsEntry("type", "MATCHED");
        assertThat(((Number) notice.get("sessionId")).longValue()).isEqualTo(room.sessionId());
        assertThat(toJoiner.poll(500, TimeUnit.MILLISECONDS)).isNull();
        // MATCHED 에는 대기 팝업 필드가 없습니다.
        assertThat(notice).doesNotContainKeys("waitedSeconds", "expiresAt");
    }

    @Test
    @DisplayName("WAIT_PROMPT 는 기다린 초와 상한 시각(+09:00)을, WAIT_EXPIRED 는 방 id 만 담는다")
    void waitNotices() throws Exception {
        Long hostId = fixture.newUser();
        BlockingQueue<Map<String, Object>> toHost = subscribeMatch(hostId);

        matchNotifier.waitPrompt(hostId, 31L, 30, LocalDateTime.of(2026, 10, 31, 12, 5));
        matchNotifier.waitExpired(hostId, 31L);

        Map<String, Object> prompt = toHost.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(prompt).containsEntry("type", "WAIT_PROMPT");
        assertThat(((Number) prompt.get("sessionId")).longValue()).isEqualTo(31L);
        assertThat(((Number) prompt.get("waitedSeconds")).longValue()).isEqualTo(30L);
        assertThat(prompt).containsEntry("expiresAt", "2026-10-31T12:05:00+09:00");
        Map<String, Object> expired = toHost.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(expired).containsEntry("type", "WAIT_EXPIRED")
                .doesNotContainKeys("waitedSeconds", "expiresAt");
    }

    /** 구독은 비동기로 등록되므로, 서버에서 탐침 메시지를 보내 받을 때까지 기다린 뒤 돌려줍니다. */
    private BlockingQueue<Map<String, Object>> subscribeMatch(Long userId) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(userId, Role.USER));
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                        new StompSessionHandlerAdapter() {
                        })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/match", new QueueFrameHandler(inbox));
        Object probe = Map.of("probe", true);
        for (int i = 0; i < 50; i++) {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/match", probe);
            Map<String, Object> received = inbox.poll(100, TimeUnit.MILLISECONDS);
            if (received != null && received.containsKey("probe")) {
                // 늦게 도착한 탐침까지 비웁니다.
                Thread.sleep(100);
                inbox.removeIf(message -> message.containsKey("probe"));
                return inbox;
            }
        }
        throw new AssertionError("구독이 등록되지 않았습니다: /user/queue/match");
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
}
