package likelion.yacha_backend.global.security.cookie;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 쿠키를 만들고, 지우고, 읽습니다.
 *
 * <p><b>한 곳에 모은 이유</b>: 쿠키는 이름 · Path · Domain 이 <b>발급할 때와 완전히 같아야</b>
 * 삭제됩니다. 로그인 쪽과 로그아웃 쪽에서 따로 만들면 Path 하나가 어긋나는 순간
 * "로그아웃했는데 쿠키가 남아 있는" 버그가 생기고, 원인을 찾기도 어렵습니다.
 *
 * <p>리프레시 토큰을 응답 body 가 아니라 쿠키로 내려보내는 이유는 {@code HttpOnly} 때문입니다.
 * JS 가 읽을 수 없어서 XSS 로 토큰을 탈취당하지 않습니다. 대신 브라우저가 알아서 실어 보냅니다.
 */
@Component
@RequiredArgsConstructor
public class CookieProvider {

    /** 프론트와 약속된 쿠키 이름입니다. 바꾸면 이미 발급된 쿠키를 지울 수 없게 되니 주의하세요. */
    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    /** 로그인 · 게스트 발급 · 재발급 응답에 실을 쿠키입니다. */
    public ResponseCookie createRefreshCookie(String refreshToken) {
        return build(refreshToken, Duration.ofMillis(jwtProperties.refreshTokenValidity()));
    }

    /**
     * 로그아웃 응답에 실을 <b>삭제용</b> 쿠키입니다.
     *
     * <p>쿠키는 "지워라" 라는 명령이 따로 없습니다. 같은 이름 · 같은 속성으로 <b>수명 0</b> 인
     * 쿠키를 다시 내려보내는 것이 삭제입니다.
     */
    public ResponseCookie deleteRefreshCookie() {
        return build("", Duration.ZERO);
    }

    /** 요청에 실려 온 리프레시 토큰을 꺼냅니다. 재발급 · 로그아웃에서 사용합니다. */
    public Optional<String> resolveRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {   // 쿠키가 하나도 없으면 null 입니다 (빈 배열이 아닙니다)
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
