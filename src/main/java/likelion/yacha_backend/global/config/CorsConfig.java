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
        // 리프레시 토큰을 HttpOnly 쿠키로 주고받기 때문에 true 여야 합니다.
        // false 면 브라우저가 쿠키를 아예 보내지도, 저장하지도 않습니다. (에러도 조용히 납니다)
        //
        // 프론트도 fetch(url, { credentials: 'include' }) 를 써야 합니다. 한쪽만 설정하면 동작하지 않습니다.
        //
        // credentials 를 허용하면 Access-Control-Allow-Origin 에 * 를 쓸 수 없습니다.
        // 위에서 setAllowedOrigins 대신 setAllowedOriginPatterns 를 쓰고 있어 괜찮습니다 —
        // 패턴에 맞는 출처를 찾아 그 출처를 그대로 응답에 적어주는 방식이라 * 가 나가지 않습니다.
        // 다만 배포의 CORS_ALLOWED_ORIGINS 에 * 를 넣으면 아무 사이트나 쿠키를 실어 호출할 수 있습니다.
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
