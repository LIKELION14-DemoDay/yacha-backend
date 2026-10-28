package likelion.yacha_backend.domain.balancegame.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.global.security.cookie.CookieProperties;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("밸런스 게임 미리보기 결과 쿠키")
class PreviewResultCookieTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final JwtProperties JWT = new JwtProperties("test-secret-key-at-least-32-bytes-long!!", 600_000L, 1_209_600_000L);
    private static final CookieProperties COOKIE = new CookieProperties(true, "Lax", "/api/v1/auth");

    private final PreviewResultCookie cookie = at(NOW);

    private static PreviewResultCookie at(Instant now) {
        return new PreviewResultCookie(JWT, COOKIE, Clock.fixed(now, ZoneId.of("Asia/Seoul")));
    }

    private static MockHttpServletRequest requestWith(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(PreviewResultCookie.COOKIE_NAME, value));
        return request;
    }

    @Test
    @DisplayName("리프레시 쿠키와 같은 설정(HttpOnly · Secure · SameSite · 경로)에 1시간 수명이다")
    void attributes() {
        ResponseCookie created = cookie.create(PreviewResult.of(PhilosopherType.HOBBES));

        assertThat(created.getName()).isEqualTo("balanceGamePreview");
        assertThat(created.isHttpOnly()).isTrue();
        assertThat(created.isSecure()).isTrue();
        assertThat(created.getSameSite()).isEqualTo("Lax");
        assertThat(created.getPath()).isEqualTo("/api/v1/auth");
        assertThat(created.getMaxAge()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("만든 결과를 그대로 읽는다 (유형 · 건너뜀)")
    void roundTrip() {
        String hobbes = cookie.create(PreviewResult.of(PhilosopherType.HOBBES)).getValue();
        String skipped = cookie.create(PreviewResult.skipped()).getValue();

        assertThat(cookie.read(requestWith(hobbes))).contains(PreviewResult.of(PhilosopherType.HOBBES));
        assertThat(cookie.read(requestWith(skipped))).hasValueSatisfying(result -> assertThat(result.isSkipped()).isTrue());
    }

    @Test
    @DisplayName("1시간이 지나면 읽지 않는다")
    void expires() {
        String value = cookie.create(PreviewResult.of(PhilosopherType.KANT)).getValue();

        assertThat(at(NOW.plus(Duration.ofMinutes(59))).read(requestWith(value))).isPresent();
        assertThat(at(NOW.plus(Duration.ofMinutes(61))).read(requestWith(value))).isEmpty();
    }

    @Test
    @DisplayName("값을 바꾸거나 다른 키로 서명했으면 읽지 않는다")
    void rejectsTampered() {
        String value = cookie.create(PreviewResult.of(PhilosopherType.KANT)).getValue();
        String[] parts = value.split("\\.");
        // 서명은 그대로 두고 내용의 유형만 KANT → NIETZSCHE 로 바꾼 경우
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("KANT");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("KANT", "NIETZSCHE").getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];
        PreviewResultCookie otherKey = new PreviewResultCookie(
                new JwtProperties("another-secret-key-at-least-32-bytes!!!", 600_000L, 1_209_600_000L), COOKIE,
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

        assertThat(cookie.read(requestWith(forged))).isEmpty();
        assertThat(cookie.read(requestWith(otherKey.create(PreviewResult.of(PhilosopherType.MILL)).getValue()))).isEmpty();
        assertThat(cookie.read(requestWith("not-a-token"))).isEmpty();
    }

    @Test
    @DisplayName("같은 키로 서명한 액세스 토큰을 넣어도 미리보기 결과로 읽지 않는다")
    void rejectsOtherTokenType() {
        String accessToken = new JwtTokenProvider(JWT).createAccessToken(1L, Role.USER);

        assertThat(cookie.read(requestWith(accessToken))).isEmpty();
    }

    @Test
    @DisplayName("쿠키가 없거나 비어 있으면 비어 있다")
    void noCookie() {
        assertThat(cookie.read(new MockHttpServletRequest())).isEmpty();
        assertThat(cookie.read(requestWith(""))).isEmpty();
    }

    @Test
    @DisplayName("삭제 쿠키는 수명이 0이고 나머지 속성은 만들 때와 같다")
    void delete() {
        ResponseCookie created = cookie.create(PreviewResult.skipped());
        ResponseCookie deleted = cookie.delete();

        assertThat(deleted.getMaxAge()).isZero();
        assertThat(deleted.getValue()).isEmpty();
        // 속성이 하나라도 다르면 브라우저가 기존 쿠키를 지우지 않음
        assertThat(deleted.getName()).isEqualTo(created.getName());
        assertThat(deleted.getPath()).isEqualTo(created.getPath());
        assertThat(deleted.isSecure()).isEqualTo(created.isSecure());
        assertThat(deleted.getSameSite()).isEqualTo(created.getSameSite());
    }
}
