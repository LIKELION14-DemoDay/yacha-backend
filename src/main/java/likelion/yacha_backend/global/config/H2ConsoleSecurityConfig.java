package likelion.yacha_backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@Profile("local")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class H2ConsoleSecurityConfig {

    @Bean
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                // 이 체인이 담당할 경로를 한정
                .securityMatcher("/h2-console/**")

                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

                // H2 콘솔은 폼 전송으로 동작
                .csrf(csrf -> csrf.disable())

                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
