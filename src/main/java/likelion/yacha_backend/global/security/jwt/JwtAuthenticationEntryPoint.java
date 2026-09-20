package likelion.yacha_backend.global.security.jwt;

import likelion.yacha_backend.global.exception.GlobalErrorCode;
import likelion.yacha_backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청(401). Security 필터에서 걸리므로 GlobalExceptionHandler 를 타지 않아,
 * 여기서 직접 ApiResponse 포맷으로 내려줍니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                          HttpServletResponse response,
                          AuthenticationException authException) throws IOException {

        log.warn("인증 실패: {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(GlobalErrorCode.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(GlobalErrorCode.UNAUTHORIZED, GlobalErrorCode.UNAUTHORIZED.getMessage()));
    }
}
