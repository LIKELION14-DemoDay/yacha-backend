package likelion.yacha_backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 프론트엔드 연동을 위한 CORS 설정.
 * 허용 주소는 application.yaml 의 {@code cors.allowed-origins} 에서 관리합니다.
 *
 * <p>정확히 일치하는 주소 대신 <b>패턴</b>으로 등록합니다.
 * 프리뷰 배포는 브랜치·커밋마다 주소가 새로 생기므로 와일드카드 패턴이 필요할 수 있습니다.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // TraceIdFilter 가 실어 보내는 X-Trace-Id 를 프론트가 읽을 수 있어야 합니다 — CORS 는
        // 노출 목록에 없는 헤더를 크로스 오리진 JS 에서 못 읽게 막습니다.
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        // JWT(Authorization 헤더)만 쓰는 지금은 false 입니다.
        // 웹 클라이언트에 세션 쿠키 인증을 붙이게 되면 true 로 바꿔야 합니다. (명세 2-1 결정 필요)
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
