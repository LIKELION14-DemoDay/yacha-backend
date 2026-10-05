package likelion.yacha_backend.global.security.jwt;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import likelion.yacha_backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                        HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {

        // 게스트가 회원 전용 기능을 부른 경우는 코드를 따로 내려줌
        // 프론트가 일반 권한 오류와 구분해 가입 안내를 띄울 수 있게
        BaseErrorCode errorCode = isGuest() ? GlobalErrorCode.GUEST_NOT_ALLOWED : GlobalErrorCode.FORBIDDEN;

        log.warn("권한 없음: {} {} ({})", request.getMethod(), request.getRequestURI(), errorCode);

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode, errorCode.getMessage()));
    }

    private boolean isGuest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof AuthUser authUser
                && authUser.role() == Role.GUEST;
    }
}
