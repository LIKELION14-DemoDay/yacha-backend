package likelion.yacha_backend.domain.auth.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml 의 {@code oauth.*}
 *
 * 클라이언트 ID는 프론트가 SDK 에 넣는 값과 같아야 함
 * 토큰의 {@code aud}가 이 값인지 검사하기 때문
 * 다르면 다른 앱에서 발급된 토큰으로 보고 거부
 *
 * 비밀 값은 아님
 */
@ConfigurationProperties(prefix = "oauth")
public record SocialProperties(Client google, Client kakao) {

    public record Client(String clientId, String issuerUri, String jwkSetUri) {

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank();
        }
    }
}
