package likelion.yacha_backend.global.security.stomp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    private static final long TIMEOUT_SECONDS = 5;

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
        CompletableFuture<String> received = new CompletableFuture<>();

        session.subscribe("/user/queue/errors", new CapturingFrameHandler(received));

        // 구독 등록은 비동기라, 받을 때까지 짧은 간격으로 다시 보냅니다.
        String payload = null;
        for (int i = 0; i < 50 && payload == null; i++) {
            messagingTemplate.convertAndSendToUser("7", "/queue/errors", "hello");
            try {
                payload = received.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // 아직 구독이 등록되지 않았습니다.
            }
        }

        assertThat(payload).isEqualTo("hello");
        assertThat(handler.errors).isNotDone();
        session.disconnect();
    }

    @Test
    @DisplayName("토론방(/topic/sessions/**) 구독은 참가자 검사가 붙기 전까지 FORBIDDEN 으로 거부한다")
    void rejectsDebateTopicSubscription() throws Exception {
        ErrorCapturingHandler handler = new ErrorCapturingHandler();
        StompSession session = connect(bearer(jwtTokenProvider.createAccessToken(1L, Role.USER)), handler);

        session.subscribe("/topic/sessions/1", new IgnoringFrameHandler());

        assertThat(handler.errors.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");
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

        String errorCode() throws Exception {
            return errors.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static class CapturingFrameHandler implements StompFrameHandler {

        private final CompletableFuture<String> received;

        CapturingFrameHandler(CompletableFuture<String> received) {
            this.received = received;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            received.complete((String) payload);
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
