package likelion.yacha_backend.domain.auth.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 {@code oauth.google-code.*}
 *
 * 웹에서 프론트가 만든 버튼으로 구글 로그인하면 id_token이 아니라 인가 코드를 받음
 * 그 코드를 토큰으로 바꾸는 데 쓰는 값
 *
 * @param clientId     웹 클라이언트 ID. 교환한 id_token의 aud가 이 값이 되므로
 *                     {@code oauth.google.client-ids}에도 들어 있어야 검증을 통과함
 * @param clientSecret 웹 클라이언트의 시크릿. 비밀 값이라 환경변수로만 받음
 * @param tokenUri     구글 토큰 발급 주소
 */
@ConfigurationProperties(prefix = "oauth.google-code")
public record GoogleCodeProperties(String clientId, String clientSecret, String tokenUri) {

    /** 구글 웹 클라이언트는 시크릿 없이는 코드를 바꿔 주지 않아서 둘 다 있어야 설정된 것으로 봄 */
    public boolean isConfigured() {
        return hasText(clientId) && hasText(clientSecret);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
