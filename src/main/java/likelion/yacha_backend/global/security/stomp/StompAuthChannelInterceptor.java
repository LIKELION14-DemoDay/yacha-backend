package likelion.yacha_backend.global.security.stomp;

import java.security.Principal;
import java.util.List;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * STOMP 프레임 단위 인증 · 인가. 클라이언트가 보낸 모든 프레임이 브로커에 닿기 전에 여기를 지납니다.
 *
 * <ul>
 *   <li><b>CONNECT</b> — {@code Authorization: Bearer} 의 액세스 토큰으로 사용자를 식별해 연결에 붙입니다.
 *       이후 프레임은 이 사용자로 처리됩니다. 토큰은 CONNECT 때만 검사하므로, 연결 중에 액세스 토큰이
 *       만료돼도 연결은 끊지 않습니다(연결 수명 = 게임 수명). 프론트는 CONNECT · 재연결 직전에 토큰을
 *       재발급해 남은 시간이 짧은 토큰으로 연결하지 않게 합니다.</li>
 *   <li><b>SUBSCRIBE</b> — 허용한 목적지만 구독할 수 있습니다. simple broker 는 구독자 전원에게 메시지를
 *       그대로 보내므로, 권한은 구독하는 순간에만 막을 수 있습니다. 토론방처럼 도메인 데이터로 판단하는
 *       목적지는 {@link StompSubscriptionAuthorizer} 구현에 맡깁니다.</li>
 *   <li><b>SEND</b> — 인증된 연결이 앱 목적지({@code /app/**})로만 보낼 수 있습니다. 브로커 목적지로 직접
 *       보내면 서버 로직을 거치지 않고 남의 개인 큐나 토론방에 메시지가 그대로 전달되므로 막습니다.</li>
 * </ul>
 *
 * <p>여기서 던진 예외는 {@link StompErrorHandler} 가 ERROR 프레임으로 바꾸고, 서버는 연결을 끊습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 본인에게만 오는 큐(매칭 알림 · 에러). Spring 이 연결된 사용자별 목적지로 바꿔 줍니다. */
    private static final String USER_QUEUE_PREFIX = "/user/queue/";

    /** 클라이언트가 보낼 수 있는 유일한 목적지. {@code @MessageMapping} 컨트롤러로 갑니다. */
    private static final String APP_DESTINATION_PREFIX = "/app/";

    private final JwtTokenProvider jwtTokenProvider;
    private final List<StompSubscriptionAuthorizer> subscriptionAuthorizers;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        // 하트비트는 command 가 없습니다.
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authenticate(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(requireUser(accessor), accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            requireUser(accessor);
            authorizeSend(accessor.getDestination());
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor.getFirstNativeHeader(AUTHORIZATION_HEADER));
        if (token == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        // HTTP 필터와 같은 방침입니다. 토큰을 해석하다 예상 밖 예외가 나도 500 이 아니라 인증 실패로 끝냅니다.
        AuthUser authUser;
        try {
            authUser = jwtTokenProvider.parseAccessUser(token)
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("STOMP CONNECT 토큰을 해석하지 못했습니다: {}", e.toString());
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        // Principal 이름은 AuthUser.getUsername() = userId 입니다. /user/queue/** 가 이 값으로 라우팅됩니다.
        accessor.setUser(new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities()));
    }

    private AuthUser requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        return AuthUser.from(user);
    }

    /**
     * 허용 목록 방식입니다. 본인 큐가 아니면 그 목적지를 맡는 {@link StompSubscriptionAuthorizer} 가 허용해야 하고,
     * 맡는 구현이 없는 목적지는 모두 거부합니다.
     */
    private void authorizeSubscribe(AuthUser user, String destination) {
        if (destination == null) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        if (destination.startsWith(USER_QUEUE_PREFIX)) {
            return;
        }
        boolean allowed = subscriptionAuthorizers.stream()
                .filter(authorizer -> authorizer.supports(destination))
                .findFirst()
                .map(authorizer -> authorizer.canSubscribe(user, destination))
                .orElse(false);
        if (!allowed) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    /**
     * 앱 목적지만 허용합니다. {@code /user/{userId}/queue/**} 나 {@code /topic/**} 로 직접 보내면 브로커가
     * 그대로 전달해, 다른 사용자에게 가짜 매칭 알림을 보내거나 남의 토론방에 끼어들 수 있습니다.
     *
     * <p>토론방으로 보내는 채팅이 참가자의 것인지는 여기가 아니라 채팅 컨트롤러가 게임 메모리로 확인합니다.
     * 거부해도 연결을 끊지 않고 {@code /user/queue/errors} 로 알려야 해서입니다 (명세 2-4).
     */
    private void authorizeSend(String destination) {
        if (destination != null && destination.startsWith(APP_DESTINATION_PREFIX)) {
            return;
        }
        throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }

    private String resolveToken(String header) {
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
