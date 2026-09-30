package likelion.yacha_backend.infra.liner;


import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 liner.* 설정값.
 *
 * API Key는 저장소에 직접 작성하지 않고
 * LINER_API_KEY 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "liner")
public record LinerProperties(
        String baseUrl,
        String apiKey
) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}