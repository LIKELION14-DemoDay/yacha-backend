package likelion.yacha_backend.domain.auth.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml 의 {@code oauth.kakao-code.*}
 *
 * 웹에서 카카오 JS SDK 는 id_token 을 바로 주지 않고 인가 코드(code)만 줌
 * 그 코드를 토큰으로 바꾸는 데 쓰는 값
 *
 * @param clientId     REST API 키. 교환한 id_token 의 aud 가 이 값이 되므로
 *                     {@code oauth.kakao.client-ids} 에도 들어 있어야 검증을 통과함
 * @param clientSecret 콘솔에서 Client Secret 을 켰을 때만 넣음. 비밀 값이라 환경변수로만 받음
 * @param tokenUri     카카오 토큰 발급 주소
 */
@ConfigurationProperties(prefix = "oauth.kakao-code")
public record KakaoCodeProperties(String clientId, String clientSecret, String tokenUri) {

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    public boolean hasClientSecret() {
        return clientSecret != null && !clientSecret.isBlank();
    }
}
