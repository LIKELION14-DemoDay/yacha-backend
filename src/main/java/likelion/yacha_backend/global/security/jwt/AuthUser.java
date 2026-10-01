package likelion.yacha_backend.global.security.jwt;

import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

/**
 * 인증된 사용자 정보. JWT 에서 꺼낸 userId/role 만 담습니다. (DB 조회 없음)
 *
 * <p>컨트롤러에서 로그인한 유저 id 를 꺼내는 방법:
 * <pre>{@code
 * @GetMapping("/sessions/me")
 * public ApiResponse<...> mySessions(@AuthenticationPrincipal AuthUser authUser) {
 *     Long userId = authUser.getUserId();
 * }
 * }</pre>
 */
public record AuthUser(Long userId, Role role) implements UserDetails {

    public Long getUserId() {
        return userId;
    }

    /** 공개 GET처럼 비로그인도 허용하는 엔드포인트에서, {@code authUser}가 null일 수 있을 때 씁니다. */
    public static Long idOrNull(AuthUser authUser) {
        return authUser == null ? null : authUser.getUserId();
    }

    /**
     * STOMP 연결에 붙인 사용자에서 {@link AuthUser} 를 꺼냅니다. CONNECT 때 {@code StompAuthChannelInterceptor} 가
     * {@code UsernamePasswordAuthenticationToken(authUser, ...)} 로 붙여 둡니다.
     */
    public static AuthUser from(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser;
        }
        throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }
}
