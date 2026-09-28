package likelion.yacha_backend.global.config;

import java.util.List;
import likelion.yacha_backend.global.security.stomp.StompAuthChannelInterceptor;
import likelion.yacha_backend.global.security.stomp.StompErrorHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * 실시간 토론용 WebSocket(STOMP) 설정. 규약은 API 명세 2-12 를 따릅니다.
 *
 * <ul>
 *   <li>엔드포인트 {@code /ws} — 핸드셰이크는 열어 두고 CONNECT 프레임에서 인증합니다</li>
 *   <li>{@code /app/**} — 클라이언트가 서버로 보내는 목적지 ({@code @MessageMapping})</li>
 *   <li>{@code /topic/**}, {@code /queue/**} — 서버가 클라이언트로 보내는 목적지 (내장 simple broker)</li>
 *   <li>{@code /user/queue/**} — 본인에게만 가는 목적지</li>
 * </ul>
 *
 * <p>simple broker 는 서버 1대 전용입니다. 여러 대로 늘리면 외부 브로커가 필요합니다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 서버 ↔ 클라이언트 하트비트 간격. 네트워크가 갑자기 끊겨 종료 신호가 오지 않는 연결(반쯤 열린 연결)을
     * 이 간격의 몇 배 안에 서버가 끊김으로 판단하고 정리합니다.
     */
    private static final long HEARTBEAT_MILLIS = 10_000;

    /** 한 클라이언트로 보내는 데 이 시간 이상 막히면 느리거나 죽은 연결로 보고 닫습니다. */
    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;

    /** 한 클라이언트에 보내지 못하고 쌓인 버퍼가 이 크기를 넘으면 연결을 닫습니다. */
    private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    /** 클라이언트가 보내는 프레임 하나의 최대 크기. 채팅 한 건을 넉넉히 담는 크기입니다. */
    private static final int MESSAGE_SIZE_LIMIT_BYTES = 64 * 1024;

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final StompErrorHandler stompErrorHandler;
    private final TaskScheduler messageBrokerTaskScheduler;
    private final List<String> allowedOrigins;

    /**
     * {@code messageBrokerTaskScheduler} 는 이 설정을 바탕으로 만들어지는 빈이라, 바로 주입하면 순환 참조가
     * 됩니다. {@code @Lazy} 로 프록시를 받아 하트비트를 처음 보낼 때 실제 빈을 씁니다 (Spring 문서의 방식).
     */
    public WebSocketConfig(
            StompAuthChannelInterceptor stompAuthChannelInterceptor,
            StompErrorHandler stompErrorHandler,
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler,
            @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.stompErrorHandler = stompErrorHandler;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 허용 Origin 은 REST 의 CORS 설정(CorsConfig)과 같은 값을 씁니다.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
        registry.setErrorHandler(stompErrorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(messageBrokerTaskScheduler);
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setSendTimeLimit(SEND_TIME_LIMIT_MILLIS)
                .setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT_BYTES)
                .setMessageSizeLimit(MESSAGE_SIZE_LIMIT_BYTES);
    }
}
