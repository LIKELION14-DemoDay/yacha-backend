package likelion.yacha_backend.global.security.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("리프레시 토큰 쿠키")
class CookieProviderTest {

    private static final long FOURTEEN_DAYS_MS = Duration.ofDays(14).toMillis();

    // 스프링 컨텍스트 없이 값만 넣어 만듭니다. 설정값에 따른 동작만 확인하면 되기 때문입니다.
    private final CookieProvider cookieProvider = new CookieProvider(
            new CookieProperties(true, "Lax", "/api/v1/auth"),
            new JwtProperties("test-secret", 600_000L, FOURTEEN_DAYS_MS));

    @Test
    @DisplayName("발급 쿠키는 HttpOnly · Secure · SameSite · Path 와 14일 수명을 갖는다")
    void createRefreshCookie() {
        ResponseCookie cookie = cookieProvider.createRefreshCookie("refresh-token-value");

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo("refresh-token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("삭제 쿠키는 수명이 0이고 나머지 속성은 발급 때와 같다")
    void deleteRefreshCookie() {
        ResponseCookie created = cookieProvider.createRefreshCookie("refresh-token-value");
        ResponseCookie deleted = cookieProvider.deleteRefreshCookie();

        assertThat(deleted.getMaxAge()).isZero();
        assertThat(deleted.getValue()).isEmpty();
        // 속성이 하나라도 다르면 브라우저가 기존 쿠키를 지우지 않습니다.
        assertThat(deleted.getName()).isEqualTo(created.getName());
        assertThat(deleted.getPath()).isEqualTo(created.getPath());
        assertThat(deleted.isSecure()).isEqualTo(created.isSecure());
        assertThat(deleted.getSameSite()).isEqualTo(created.getSameSite());
    }

    @Test
    @DisplayName("요청 쿠키에서 리프레시 토큰을 꺼낸다")
    void resolveRefreshToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("refreshToken", "token-value"));

        assertThat(cookieProvider.resolveRefreshToken(request)).contains("token-value");
    }

    @Test
    @DisplayName("쿠키가 아예 없으면 빈 값이다")
    void resolveReturnsEmptyWhenNoCookies() {
        assertThat(cookieProvider.resolveRefreshToken(new MockHttpServletRequest())).isEmpty();
    }

    @Test
    @DisplayName("값이 빈 쿠키는 없는 것으로 본다 (로그아웃 직후)")
    void resolveReturnsEmptyWhenBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", ""));

        assertThat(cookieProvider.resolveRefreshToken(request)).isEmpty();
    }
}
