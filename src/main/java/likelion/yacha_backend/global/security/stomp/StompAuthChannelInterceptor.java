package likelion.yacha_backend.global.security.stomp;

import java.security.Principal;
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
 *       이후 프레임은 이 사용자로 처리됩니다.</li>
 *   <li><b>SUBSCRIBE</b> — 허용한 목적지만 구독할 수 있습니다. simple broker 는 구독자 전원에게 메시지를
 *       그대로 보내므로, 권한은 구독하는 순간에만 막을 수 있습니다.</li>
 *   <li><b>SEND</b> — CONNECT 에서 인증된 연결만 보낼 수 있습니다.</li>
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

    private final JwtTokenProvider jwtTokenProvider;

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
            requireUser(accessor);
            authorizeSubscribe(accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            requireUser(accessor);
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

    private void requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * 허용 목록 방식입니다. 목록에 없는 목적지는 모두 거부합니다.
     *
     * <p>토론방({@code /topic/sessions/{id}})은 참가자 · 관전자 검사가 필요한데, 그 판단에 쓸 세션 ·
     * 참가자 테이블이 아직 없습니다. 검사 없이 열어 두면 번호만 바꿔 남의 토론을 엿볼 수 있으므로,
     * 검사를 붙일 때까지는 거부합니다.
     */
    private void authorizeSubscribe(String destination) {
        if (destination != null && destination.startsWith(USER_QUEUE_PREFIX)) {
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
