package likelion.yacha_backend.global.security.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml 의 {@code auth.cookie.*} 설정값.
 *
 * @param secure   HTTPS 에서만 쿠키를 보낼지. local(http) 에서는 false 여야 브라우저가
 *                 쿠키를 저장. 배포는 반드시 true.
 * @param sameSite 다른 사이트에서 시작된 요청에 쿠키를 붙일지. 프론트와 백엔드가 같은 도메인
 *                 (예: yacha.com / api.yacha.com)이면 {@code Lax}, 완전히 다른 도메인이면
 *                 {@code None} 이 필요하고 이때 {@code secure} 는 반드시 true 여야 합니다.
 * @param path     쿠키를 보낼 경로
 */
@ConfigurationProperties(prefix = "auth.cookie")
public record CookieProperties(
        boolean secure,
        String sameSite,
        String path
) {
}
