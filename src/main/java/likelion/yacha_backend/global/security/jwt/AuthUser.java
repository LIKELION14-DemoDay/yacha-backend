package likelion.yacha_backend.global.security.jwt;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

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
