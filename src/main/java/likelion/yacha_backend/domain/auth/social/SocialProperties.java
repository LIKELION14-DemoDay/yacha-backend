package likelion.yacha_backend.domain.auth.social;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 {@code oauth.*}
 *
 * 클라이언트 ID는 프론트가 SDK에 넣는 값과 같아야 함
 * 토큰의 {@code aud}가 이 값인지 검사하기 때문
 * 다르면 다른 앱에서 발급된 토큰으로 보고 거부
 *
 * 비밀 값은 아님
 */
@ConfigurationProperties(prefix = "oauth")
public record SocialProperties(Client google, Client kakao) {

    /**
     * @param clientIds  허용할 클라이언트 ID 목록. 구글은 웹 · iOS · Android의 클라이언트 ID가
     *                   각각 달라서, 앱이 붙으면 여러 개가 됨
     *                   그중 하나와 맞으면 통과시킴
     * @param issuerUris 허용할 발급자 목록. 구글은 {@code https://accounts.google.com}과
     *                   {@code accounts.google.com} 두 가지를 쓸 수 있다고 안내
     * @param jwkSetUri  공개키 위치. 라이브러리가 받아서 캐시
     */
    public record Client(List<String> clientIds, List<String> issuerUris, String jwkSetUri) {

        public boolean isConfigured() {
            return clientIds != null && clientIds.stream().anyMatch(id -> id != null && !id.isBlank());
        }
    }
}
