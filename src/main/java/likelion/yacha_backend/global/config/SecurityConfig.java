package likelion.yacha_backend.global.config;

import likelion.yacha_backend.domain.auth.mail.MailProperties;
import likelion.yacha_backend.domain.auth.repository.PasswordResetProperties;
import likelion.yacha_backend.domain.auth.service.GuestCleanupProperties;
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
@EnableConfigurationProperties({JwtProperties.class, CookieProperties.class, MailProperties.class,
        PasswordResetProperties.class, GuestCleanupProperties.class})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    /** 인증 없이 접근할 수 있는 경로 */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/token",
            "/api/v1/auth/token/refresh",
            "/api/v1/auth/guest",
            "/api/v1/auth/social",
            "/api/v1/auth/password/reset-request",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/social/kakao",
            // WebSocket 핸드셰이크. 브라우저는 핸드셰이크에 Authorization 헤더를 붙일 수 없어서
            // 여기서는 열어 두고, STOMP CONNECT 프레임의 JWT 로 인증합니다 (StompAuthChannelInterceptor).
            "/ws/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
    };

    /** 인증 없이 GET만 허용할 경로. 주제 · 카테고리 */
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/topics/today",
            "/api/v1/topics",
            "/api/v1/topics/{topicId:[0-9]+}",
            "/api/v1/categories",
    };

    /**
     * 게스트(비회원)도 쓸 수 있는 경로. 로그인(게스트 토큰)은 필요
     *
     * 비회원은 관전 · 게임만 할 수 있음
     * 여기에 없는 경로는 모두 회원 전용이라, 새 기능은 따로 막지 않아도 회원 전용이 됨
     * 게임 · 관전에 쓰는 경로를 새로 만들면 여기에 추가해야 함
     */
    private static final String[] GUEST_ENDPOINTS = {
            "/api/v1/auth/logout",
            // 게스트 전용. 회원이 부르면 서비스에서 ALREADY_MEMBER
            "/api/v1/auth/upgrade",
            // 게임 · 관전 (방 · 상태 · 메시지 · 주장 등). 회원 전용인 전투 기록(GET /sessions/me)은 위에서 먼저 막음
            "/api/v1/sessions/**",
    };

    /** 게스트도 GET 으로 쓸 수 있는 경로 */
    private static final String[] GUEST_GET_ENDPOINTS = {
            // 내 정보. 게스트는 isGuest 로 화면을 나눔
            "/api/v1/users/me",
            // 랜덤 주제 등 로그인이 필요한 주제 조회
            "/api/v1/topics/**",
    };

    /** 서블릿 컨테이너 자동 등록을 끔 */
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
                // JWT를 쓰므로 세션을 만들지 않음. 따라서 CSRF 토큰도 필요 없음.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 전투 기록(승패)은 회원 전용. 아래 /sessions/** 보다 먼저 와야 함
                        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/me").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(GUEST_ENDPOINTS).authenticated()
                        .requestMatchers(HttpMethod.GET, GUEST_GET_ENDPOINTS).authenticated()
                        // 나머지는 회원 전용. 게스트가 오면 403 GUEST_NOT_ALLOWED (JwtAccessDeniedHandler)
                        .anyRequest().hasAnyRole("USER", "ADMIN")
                )

                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)   // 401
                        .accessDeniedHandler(jwtAccessDeniedHandler)             // 403
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 이메일/비밀번호 회원가입·로그인에서 비밀번호를 암호화/검증하는 데 사용 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
