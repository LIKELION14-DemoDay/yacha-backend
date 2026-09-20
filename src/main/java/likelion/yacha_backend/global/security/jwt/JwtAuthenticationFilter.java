package likelion.yacha_backend.global.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        // 여기서 예외가 나가면 GlobalExceptionHandler 가 못 잡습니다 — @RestControllerAdvice 는
        // DispatcherServlet 안에서만 동작하고 필터는 그 바깥이라, 컨테이너 기본 에러 페이지가
        // 나갑니다. 프론트는 { success, data, error, traceId } 를 기대하는데 파싱 불가능한
        // HTML 을 받고, 401 로 복구 가능한 상황이 500 이 됩니다.
        //
        // 서명 검증만으로는 부족합니다 — claim 을 해석하는 과정(subject 를 Long 으로, role 을
        // enum 으로)에서 값이 예상 밖이면 unchecked 예외가 납니다. parseAccessUser 가 그 해석까지
        // 포함해 한 번에 처리하고, 실패 이유는 그쪽에서 로그로 남깁니다.
        try {
            if (token != null) {
                jwtTokenProvider.parseAccessUser(token).ifPresent(authUser -> {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        } catch (RuntimeException e) {
            // 인증을 걸지 않고 통과시킵니다. 뒤의 JwtAuthenticationEntryPoint 가 정상적인
            // 401 ApiResponse 를 내보냅니다.
            SecurityContextHolder.clearContext();
            log.warn("토큰을 해석하지 못해 인증 없이 진행합니다: {} {} ({})",
                    request.getMethod(), request.getRequestURI(), e.toString());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
