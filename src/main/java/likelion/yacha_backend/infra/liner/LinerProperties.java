
package likelion.yacha_backend.infra.liner;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * Liner API 공통 설정.
 *
 * application.yaml의 liner.* 값을 사용한다.
 * API 키는 환경변수를 통해 주입한다.
 */
@Validated
@ConfigurationProperties(prefix = "liner")
public record LinerProperties(
        @NotBlank String baseUrl,
        String apiKey,
        @NotBlank String model
) {

    /**
     * Spring Boot가 설정값을 바인딩할 때 사용하는 생성자.
     */
    @ConstructorBinding
    public LinerProperties {
    }

    /**
     * 기존 LinerClient 테스트와의 호환성을 유지한다.
     */
    public LinerProperties(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, "liner-mark");
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
