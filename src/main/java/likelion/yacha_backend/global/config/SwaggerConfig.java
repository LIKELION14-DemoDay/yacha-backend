package likelion.yacha_backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 *
 * <p>우측 상단 <b>Authorize</b> 버튼에 액세스 토큰을 넣으면 이후 요청에 자동으로
 * {@code Authorization: Bearer ...} 헤더가 붙습니다. ("Bearer "는 빼고 토큰만 붙여넣으세요.)
 */
@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("야차철학 API")
                .version("v1")
                .description("""
                        야차철학 API 문서입니다.

                        - 모든 응답은 `{ "success": ..., "data": ..., "error": ..., "traceId": ... }` 형식입니다.
                        - 회원가입·로그인·게스트 발급을 제외한 대부분의 API는 인증이 필요합니다.
                        """);

        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        return new OpenAPI()
                .info(info)
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearerScheme));
    }
}
