package likelion.yacha_backend.global.security.cookie;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 리프레시 토큰 쿠키를 만들고, 지우고, 읽음 */
@Component
@RequiredArgsConstructor
public class CookieProvider {

    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    /** 로그인 · 게스트 발급 · 재발급 응답에 실을 쿠키 */
    public ResponseCookie createRefreshCookie(String refreshToken) {
        return build(refreshToken, Duration.ofMillis(jwtProperties.refreshTokenValidity()));
    }

    /** 로그아웃 응답에 실을 삭제용 쿠키 */
    public ResponseCookie deleteRefreshCookie() {
        return build("", Duration.ZERO);
    }

    /** 요청에 실려 온 리프레시 토큰을 꺼냄. 재발급 · 로그아웃에서 사용. */
    public Optional<String> resolveRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {   // 쿠키가 하나도 없으면 null
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> REFRESH_TOKEN_COOKIE.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)                        // JS 접근 차단 (XSS 방어)
                .secure(cookieProperties.secure())     // 배포는 true, 로컬은 false
                .sameSite(cookieProperties.sameSite()) // CSRF 방어
                .path(cookieProperties.path())         // /api/v1/auth 이하에서만 전송
                .maxAge(maxAge)
                .build();
    }
}
