package likelion.yacha_backend.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import likelion.yacha_backend.domain.auth.dto.AuthResponse;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.dto.LoginRequest;
import likelion.yacha_backend.domain.auth.dto.SignupRequest;
import likelion.yacha_backend.domain.auth.dto.UpgradeRequest;
import likelion.yacha_backend.domain.auth.service.AuthService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.cookie.CookieProvider;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @Operation(
            summary = "토큰 재발급",
            description = """
                    액세스 토큰이 만료됐을 때(401) 호출
                    쿠키의 리프레시 토큰으로 인증하므로 `Authorization` 헤더는 필요 없음

                    새 액세스 토큰과 새 리프레시 토큰 쿠키가 함께 내려감
                    이전 리프레시 토큰은 그 즉시 무효

                    프론트는 재발급 요청을 하나로 묶어서 보내야 함
                    401 을 받은 요청마다 따로 호출하면, 먼저 성공한 요청이 토큰을 바꾼 뒤라 
                    나머지가 이미 사용된 토큰 으로 판정되어 로그아웃됨

                    에러
                    - `INVALID_REFRESH_TOKEN` (401): 쿠키 없음 · 만료 · 이미 사용됨 (원인 구분 없음)
                    """)
    @SecurityRequirements
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> reissue(HttpServletRequest request) {
        String refreshToken = cookieProvider.resolveRefreshToken(request).orElse(null);
        return withRefreshCookie(authService.reissue(refreshToken));
    }

    @Operation(
            summary = "로그아웃",
            description = """
                    서버에 저장된 리프레시 토큰을 지우고 쿠키를 만료

                    이미 발급된 액세스 토큰은 서버가 막을 수 없음
                    최대 10분 뒤 만료되며, 프론트도 메모리의 액세스 토큰을 버려야 함
                    """)
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal AuthUser authUser) {
        authService.logout(authUser.getUserId());

        // 서버 쪽(저장소)과 클라이언트 쪽(쿠키)을 모두 지워야 로그아웃이 끝납니다.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieProvider.deleteRefreshCookie().toString())
                .body(ApiResponse.noContent());
    }

    @Operation(
            summary = "게스트 → 회원 승격",
            description = """
                    게스트 계정에 이메일·비밀번호를 붙여 회원으로 만듦

                    같은 계정을 그대로 씀
                    게스트로 한 토론 기록이 그대로 이어짐
                    승격 대상은 요청 body가 아닌 토큰의 사용자

                    승격 후 토큰이 새로 발급되므로, 프론트는 응답의 새 `accessToken`으로 교체

                    에러
                    - `ALREADY_MEMBER` (409): 이미 회원인 계정
                    - `EMAIL_ALREADY_EXISTS` (409): 다른 사람이 쓰고 있는 이메일
                    """)
    @PostMapping("/upgrade")
    public ResponseEntity<ApiResponse<AuthResponse>> upgrade(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody UpgradeRequest request) {
        return withRefreshCookie(authService.upgrade(authUser.getUserId(), request));
    }

    /** 토큰 두 개를 HTTP 응답으로 포장 */
    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieProvider.createRefreshCookie(tokens.refreshToken()).toString())
                .body(ApiResponse.success(tokens.toResponse()));
    }
}
