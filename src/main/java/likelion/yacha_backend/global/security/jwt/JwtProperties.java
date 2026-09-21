package likelion.yacha_backend.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml 의 jwt.* 설정값.
 *
 * @param secret               HS256 서명 키. dev/prod에서는 반드시 환경변수 JWT_SECRET 로 주입할 것
 * @param accessTokenValidity  액세스 토큰 유효시간 (ms)
 * @param refreshTokenValidity 리프레시 토큰 유효시간 (ms)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidity,
        long refreshTokenValidity
) {
}
