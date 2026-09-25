package likelion.yacha_backend.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import likelion.yacha_backend.domain.auth.dto.AuthResponse;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.dto.LoginRequest;
import likelion.yacha_backend.domain.auth.dto.SignupRequest;
import likelion.yacha_backend.domain.auth.service.AuthService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.cookie.CookieProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(
            summary = "회원가입",
            description = """
                    회원 계정을 만들고 바로 토큰까지 발급
                    따로 로그인할 필요가 없음

                    - 이메일은 소문자로 저장
                    - 비밀번호는 8자 이상 72자 이하 (BCrypt 입력 상한)
                    - 게스트가 쓰던 기록을 이어가려면 이 API 가 아니라 `/auth/upgrade`
                      여기서는 새 계정이 만들어짐

                    에러
                    - `VALIDATION_FAILED` (400): 형식·길이 위반
                    - `EMAIL_ALREADY_EXISTS` (409): 이미 가입된 이메일
                    """)
    @SecurityRequirements
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return withRefreshCookie(authService.signup(request));
    }

    @Operation(
            summary = "로그인",
            description = """
                    이메일·비밀번호로 로그인하고 토큰을 발급

                    - 이메일 대소문자는 무시
                    - 실패 원인(이메일 없음 / 비밀번호 불일치)은 구분해서 알려주지 않음

                    에러
                    - `LOGIN_FAILED` (401): 이메일 또는 비밀번호 불일치
                    """)
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    @Operation(
            summary = "로그인 (토큰 발급)",
            description = """
                    `/auth/login`과 같은 동작

                    세션을 쓰지 않고 JWT 로 통일하면서 둘이 같아졌습니다. 
                    프론트가 쓰는 쪽을 정하면 나머지는 정리
                    """)
    @SecurityRequirements
    @PostMapping("/token")
    public ResponseEntity<ApiResponse<AuthResponse>> token(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    /** 토큰 두 개를 HTTP 응답으로 포장 */
    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieProvider.createRefreshCookie(tokens.refreshToken()).toString())
                .body(ApiResponse.success(tokens.toResponse()));
    }
}
