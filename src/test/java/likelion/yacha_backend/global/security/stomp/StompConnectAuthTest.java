package likelion.yacha_backend.global.security.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.crypto.SecretKey;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.ConnectionLostException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("WebSocket(STOMP) 연결 인증 · 구독 권한")
class StompConnectAuthTest {

    private static final long TIMEOUT_SECONDS = 10;

    /** 연결한 뒤 거부되는 동작을 ERROR 프레임 없이 연결만 끊겼을 때 다시 시도하는 횟수 ({@link #errorCodeAfterConnect}) */
    private static final int MAX_ATTEMPTS = 3;

    @Value("${local.server.port}")
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler clientScheduler;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
        // receipt 추적에 스케줄러가 필요합니다. 하트비트는 주고받지 않습니다.
        clientScheduler = new ThreadPoolTaskScheduler();
        clientScheduler.initialize();
        stompClient.setTaskScheduler(clientScheduler);
        stompClient.setDefaultHeartbeat(new long[]{0, 0});
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
        clientScheduler.shutdown();
    }

    @Test
    @DisplayName("유효한 액세스 토큰으로 CONNECT 하면 연결된다")
    void connectsWithValidAccessToken() throws Exception {
        StompSession session = connect(bearer(jwtTokenProvider.createAccessToken(1L, Role.USER)), new ErrorCapturingHandler());

        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("토큰 없이 CONNECT 하면 UNAUTHORIZED ERROR 프레임을 받고 연결되지 않는다")
    void rejectsConnectWithoutToken() throws Exception {
        ErrorCapturingHandler handler = new ErrorCapturingHandler();

        assertConnectFails(null, handler);
        assertThat(handler.errorCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 거부한다")
    void rejectsNonBearerHeader() throws Exception {
        ErrorCapturingHandler handler = new ErrorCapturingHandler();

        assertConnectFails(jwtTokenProvider.createAccessToken(1L, Role.USER), handler);
        assertThat(handler.errorCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("다른 키로 서명한(위조) 토큰은 거부한다")
    void rejectsForgedToken() throws Exception {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-key-that-is-long-enough-for-hs256!!".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("1")
                .claim("type", "access")
                .claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();
        ErrorCapturingHandler handler = new ErrorCapturingHandler();

        assertConnectFails(bearer(forged), handler);
        assertThat(handler.errorCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void rejectsExpiredToken() throws Exception {
        JwtTokenProvider expiredIssuer = new JwtTokenProvider(new JwtProperties(jwtSecret, -60_000, -60_000));
        ErrorCapturingHandler handler = new ErrorCapturingHandler();

        assertConnectFails(bearer(expiredIssuer.createAccessToken(1L, Role.USER)), handler);
        assertThat(handler.errorCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("리프레시 토큰으로는 연결할 수 없다")
    void rejectsRefreshToken() throws Exception {
        ErrorCapturingHandler handler = new ErrorCapturingHandler();

        assertConnectFails(bearer(jwtTokenProvider.createRefreshToken(1L)), handler);
        assertThat(handler.errorCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("본인 큐(/user/queue/**)를 구독하면 userId 로 보낸 메시지를 받는다")
    void allowsUserQueueSubscription() throws Exception {
        ErrorCapturingHandler handler = new ErrorCapturingHandler();
        StompSession session = connect(bearer(jwtTokenProvider.createAccessToken(7L, Role.USER)), handler);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        session.subscribe("/user/queue/errors", new CapturingFrameHandler(received));

        assertThat(sendUntilReceived("7", "hello", received)).isEqualTo("hello");
        assertThat(handler.errors).isNotDone();
        session.disconnect();
    }

    @Test
    @DisplayName("없는 토론방(/topic/sessions/{id}) 구독은 FORBIDDEN 으로 거부한다")
    void rejectsUnknownDebateTopicSubscription() throws Exception {
        String errorCode = errorCodeAfterConnect(1L,
                session -> session.subscribe("/topic/sessions/999999", new IgnoringFrameHandler()));

        assertThat(errorCode).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("맡는 곳이 없는 목적지(/topic/**) 구독은 FORBIDDEN 으로 거부한다")
    void rejectsUnknownTopicSubscription() throws Exception {
        String errorCode = errorCodeAfterConnect(1L,
                session -> session.subscribe("/topic/anything", new IgnoringFrameHandler()));

        assertThat(errorCode).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("다른 사용자의 개인 큐(/user/{userId}/queue/**)로 SEND 하면 FORBIDDEN 으로 거부하고 전달하지 않는다")
    void rejectsSendToOtherUserQueue() throws Exception {
        StompSession receiver = connect(bearer(jwtTokenProvider.createAccessToken(7L, Role.USER)), new ErrorCapturingHandler());
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        receiver.subscribe("/user/queue/errors", new CapturingFrameHandler(received));
        // 구독이 등록되기 전에 보내면, 권한 검사가 없어도 전달되지 않아 테스트가 통과해 버립니다.
        assertThat(sendUntilReceived("7", "probe", received)).isEqualTo("probe");

        String errorCode = errorCodeAfterConnect(8L, sender -> sender.send("/user/7/queue/errors", "spoofed"));

        assertThat(errorCode).isEqualTo("FORBIDDEN");
        Thread.sleep(500);
        assertThat(received).doesNotContain("spoofed");
        receiver.disconnect();
    }

    @Test
    @DisplayName("브로커 목적지(/topic/**)로 직접 SEND 하면 FORBIDDEN 으로 거부한다")
    void rejectsSendToBrokerDestination() throws Exception {
        String errorCode = errorCodeAfterConnect(1L, session -> session.send("/topic/sessions/1", "hello"));

        assertThat(errorCode).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("허용하지 않은 Origin 의 핸드셰이크는 거부한다")
    void rejectsDisallowedOrigin() {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://evil.example");

        assertThatThrownBy(() -> stompClient
                .connectAsync(url(), handshakeHeaders, connectHeaders(bearer(jwtTokenProvider.createAccessToken(1L, Role.USER))),
                        new ErrorCapturingHandler())
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    // ------------------------------------------------------------------

    private StompSession connect(String authorization, ErrorCapturingHandler handler) throws Exception {
        return stompClient
                .connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(authorization), handler)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 구독 등록은 비동기라, 받을 때까지 짧은 간격으로 다시 보냅니다. 받은 메시지를 돌려주고, 끝내 못 받으면 null.
     * 먼저 보낸 것이 늦게 도착해 같은 메시지를 더 받을 수 있습니다.
     */
    private String sendUntilReceived(String userId, String payload, BlockingQueue<String> received)
            throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/errors", payload);
            String message = received.poll(100, TimeUnit.MILLISECONDS);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    /**
     * 연결한 뒤 {@code action} 을 하고, 서버가 보낸 ERROR 프레임의 에러 코드를 돌려줍니다.
     *
     * <p>서버가 CONNECTED 를 보내는 도중에 ERROR 를 보내면, Spring 은 ERROR 를 보내지 못한 채 버퍼에 두고
     * 바로 연결을 닫아 ERROR 가 버려질 수 있습니다(#48). 그래서 ERROR 없이 연결만 끊기면 새로 연결해 다시 시도합니다.
     * 연결이 끊긴 것 자체가 서버가 거부했다는 뜻이라, 다시 시도해도 거부를 확인하는 테스트의 의미는 그대로입니다.
     */
    private String errorCodeAfterConnect(long userId, Consumer<StompSession> action) throws Exception {
        for (int attempt = 1; ; attempt++) {
            ErrorCapturingHandler handler = new ErrorCapturingHandler();
            action.accept(connect(bearer(jwtTokenProvider.createAccessToken(userId, Role.USER)), handler));
            try {
                return handler.errorCode();
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof ConnectionLostException) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    /** ERROR 프레임을 받고 연결이 성립하지 않는지 확인합니다. */
    private void assertConnectFails(String authorization, ErrorCapturingHandler handler) throws Exception {
        CompletableFuture<StompSession> future = stompClient
                .connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(authorization), handler);

        handler.errors.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(future.isDone() && !future.isCompletedExceptionally()).isFalse();
    }

    private StompHeaders connectHeaders(String authorization) {
        StompHeaders headers = new StompHeaders();
        if (authorization != null) {
            headers.add("Authorization", authorization);
        }
        return headers;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String url() {
        return "ws://localhost:" + port + "/ws";
    }

    /** 서버가 보낸 ERROR 프레임의 message 헤더(에러 코드)를 잡아 둡니다. */
    private static class ErrorCapturingHandler extends StompSessionHandlerAdapter {

        private final CompletableFuture<String> errors = new CompletableFuture<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
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

        /**
         * 연결이 끊김. ERROR 프레임을 받은 뒤라면 이미 끝난 future 라 아무 일도 없고,
         * ERROR 없이 끊겼으면 시간 초과까지 기다리지 않고 바로 실패하게 합니다.
         */
        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            errors.completeExceptionally(exception);
        }

        String errorCode() throws Exception {
            return errors.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static class CapturingFrameHandler implements StompFrameHandler {

        private final BlockingQueue<String> received;

        CapturingFrameHandler(BlockingQueue<String> received) {
            this.received = received;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            received.add((String) payload);
        }
    }

    private static class IgnoringFrameHandler implements StompFrameHandler {

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
        }
    }
}
