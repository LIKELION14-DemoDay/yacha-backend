package likelion.yacha_backend.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import likelion.yacha_backend.domain.auth.dto.AuthResponse;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.service.AuthService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.cookie.CookieProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "게스트 · 회원가입 · 로그인 · 토큰")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieProvider cookieProvider;

    @Operation(
            summary = "게스트 생성",
            description = """
                    게스트 계정을 만들고 토큰을 발급. 로그인 없이 토론에 참여하려면 먼저 호출.

                    - 응답 body 의 `accessToken` 을 이후 요청의 `Authorization: Bearer` 헤더에 넣음
                    - 리프레시 토큰은 HttpOnly 쿠키로 내려감
                      JS 로 읽을 수 없고, 재발급 시 브라우저가 자동으로 실어 보냄
                    """)
    @SecurityRequirements   // 전역 bearerAuth를 끔. 인증 없이 호출
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<AuthResponse>> createGuest() {
        return withRefreshCookie(authService.createGuest());
    }

    /** 토큰 두 개를 HTTP 응답으로 포장 */
    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieProvider.createRefreshCookie(tokens.refreshToken()).toString())
                .body(ApiResponse.success(tokens.toResponse()));
    }
}
