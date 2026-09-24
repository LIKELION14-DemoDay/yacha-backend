package likelion.yacha_backend.global.config;

import likelion.yacha_backend.global.security.cookie.CookieProperties;
import likelion.yacha_backend.global.security.jwt.JwtAccessDeniedHandler;
import likelion.yacha_backend.global.security.jwt.JwtAuthenticationEntryPoint;
import likelion.yacha_backend.global.security.jwt.JwtAuthenticationFilter;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({JwtProperties.class, CookieProperties.class})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * 인증 없이 접근할 수 있는 경로. API 명세의 인증 "—" 항목입니다.
     *
     * <p>{@code /auth/**} 로 통째로 열지 않는 이유는 {@code /auth/logout}, {@code /auth/upgrade} 는
     * 인증이 필요하기 때문입니다.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/token",
            "/api/v1/auth/token/refresh",
            "/api/v1/auth/guest",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
    };

    /**
     * 인증 없이 <b>GET 만</b> 허용할 경로. 주제 · 카테고리와 공개 페이지입니다.
     *
     * <p>id 를 숫자로 제한한 것도 의도입니다. {@code /topics/*} 로 두면 나중에
     * 누군가 {@code /topics/mine} 같은 걸 추가했을 때 조용히 공개됩니다.
     * {@code /sessions/me} 와 {@code /sessions/{id}} 가 겹치는 문제도 같은 방식으로 막을 수 있습니다.
     */
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/topics/today",
            "/api/v1/topics",
            "/api/v1/topics/{topicId:[0-9]+}",
            "/api/v1/categories",
            "/api/v1/public/sessions",
            "/api/v1/public/sessions/{sessionId:[0-9]+}",
    };

    /**
     * 서블릿 컨테이너 자동 등록을 끕니다.
     *
     * <p>Boot 는 {@code Filter} 빈을 보면 서블릿 필터로 자동 등록하는데, 아래
     * {@code addFilterBefore} 가 같은 인스턴스를 시큐리티 체인에도 넣습니다.
     * {@code OncePerRequestFilter} 의 중복 실행 방지 덕에 지금은 두 번 돌지 않지만,
     * <b>사본이 시큐리티 체인 바깥에 존재하는 상태</b>라 누군가
     * {@code securityMatcher} 나 두 번째 {@code SecurityFilterChain} 을 추가하면
     * 그 경로에서는 보호가 사라집니다. 등록 위치를 시큐리티 체인 하나로 못박습니다.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // JWT를 쓰므로 세션을 만들지 않습니다. 따라서 CSRF 토큰도 필요 없습니다.
                // 웹 클라이언트에 세션(쿠키) 인증을 붙이게 되면 이 두 설정을 다시 봐야 합니다. (명세 2-1 결정 필요)
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // CORS preflight
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 명세에서 인증 "선택" 인 API(/sessions 등)도 게스트 JWT 가 필요하므로 여기서 걸립니다.
                        .anyRequest().authenticated()
                )

                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)   // 401
                        .accessDeniedHandler(jwtAccessDeniedHandler)             // 403
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 이메일/비밀번호 회원가입·로그인에서 비밀번호를 암호화/검증하는 데 사용합니다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
