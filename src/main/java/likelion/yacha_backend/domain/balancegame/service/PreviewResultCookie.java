package likelion.yacha_backend.domain.balancegame.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.global.security.cookie.CookieProperties;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 로그인 전 밸런스 게임 결과를 담는 쿠키
 *
 * 온보딩 미리보기에서 내려 두고, 로그인 · 회원가입 응답에서 읽어 계정에 저장함
 * 리프레시 쿠키와 같은 설정(보안 · SameSite · 경로 /api/v1/auth)이라 로그인 요청에만 실림
 *
 * 값은 JWT 키로 서명해서 사용자가 원하는 유형으로 바꿔 넣을 수 없음
 * 액세스 · 리프레시 토큰과 종류(type)가 달라 서로 대신 쓰일 수 없음
 */
@Slf4j
@Component
public class PreviewResultCookie {

    public static final String COOKIE_NAME = "balanceGamePreview";

    /** 온보딩 뒤 로그인 · 가입을 마칠 시간. 게스트로 시작한 뒤 소셜로 가입해도 이어지는 시간이기도 함 */
    static final Duration VALIDITY = Duration.ofHours(1);

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String TOKEN_TYPE = "balance-game-preview";
    private static final String RESULT_CLAIM = "result";
    private static final String SKIPPED = "SKIPPED";

    private final SecretKey key;
    private final CookieProperties cookieProperties;
    private final Clock clock;

    public PreviewResultCookie(JwtProperties jwtProperties, CookieProperties cookieProperties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.cookieProperties = cookieProperties;
        this.clock = clock;
    }

    /** 미리보기 응답에 실을 쿠키. 다시 하면 새 쿠키로 덮어씀 */
    public ResponseCookie create(PreviewResult result) {
        Instant now = clock.instant();
        String token = Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE)
                .claim(RESULT_CLAIM, result.isSkipped() ? SKIPPED : result.type().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(VALIDITY)))
                .signWith(key)
                .compact();
        return build(token, VALIDITY);
    }

    /** 저장을 마쳤거나 잘못된 쿠키를 지울 때 */
    public ResponseCookie delete() {
        return build("", Duration.ZERO);
    }

    /**
     * 요청에 실려 온 결과
     *
     * @return 서명이 맞고 만료되지 않은 결과. 쿠키가 없거나 잘못됐으면 비어 있음
     */
    public Optional<PreviewResult> read(HttpServletRequest request) {
        return findValue(request).flatMap(this::parse);
    }

    private Optional<String> findValue(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private Optional<PreviewResult> parse(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // 만료 · 서명 불일치 · 형식 오류. 로그인은 막지 않고 결과만 버림
            log.debug("밸런스 게임 미리보기 쿠키를 읽지 못했습니다: {}", e.getMessage());
            return Optional.empty();
        }
        if (!TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            log.warn("밸런스 게임 미리보기 쿠키가 아닌 토큰이 실려 왔습니다");
            return Optional.empty();
        }

        String result = claims.get(RESULT_CLAIM, String.class);
        if (SKIPPED.equals(result)) {
            return Optional.of(PreviewResult.skipped());
        }
        try {
            return Optional.of(PreviewResult.of(PhilosopherType.valueOf(result)));
        } catch (IllegalArgumentException | NullPointerException e) {
            // 유형 이름이 바뀐 뒤 이전 배포가 만든 쿠키 등
            log.warn("밸런스 게임 미리보기 쿠키의 유형을 모릅니다: {}", result);
            return Optional.empty();
        }
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path())
                .maxAge(maxAge)
                .build();
    }
}
