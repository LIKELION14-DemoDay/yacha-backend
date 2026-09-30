package likelion.yacha_backend.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import likelion.yacha_backend.domain.auth.dto.AuthResponse;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.dto.KakaoCodeLoginRequest;
import likelion.yacha_backend.domain.auth.dto.LoginRequest;
import likelion.yacha_backend.domain.auth.dto.PasswordResetConfirmRequest;
import likelion.yacha_backend.domain.auth.dto.PasswordResetRequest;
import likelion.yacha_backend.domain.auth.dto.SignupRequest;
import likelion.yacha_backend.domain.auth.dto.SocialLoginRequest;
import likelion.yacha_backend.domain.auth.dto.UpgradeRequest;
import likelion.yacha_backend.domain.auth.service.AuthService;
import likelion.yacha_backend.domain.auth.service.PasswordResetService;
import likelion.yacha_backend.domain.auth.service.KakaoLoginService;
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
    private final KakaoLoginService kakaoLoginService;
    private final CookieProvider cookieProvider;
    private final AuthResponseFactory authResponseFactory;
    private final PasswordResetService passwordResetService;

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
            summary = "소셜 로그인 (카카오 · 구글)",
            description = """
                    프론트가 카카오 · 구글 SDK 로 받은 **id_token** 을 보내면, 서버가 서명을 확인하고
                    우리 토큰을 발급합니다. 응답 형태는 일반 로그인과 같습니다.

                    ```json
                    { "provider": "KAKAO", "idToken": "eyJ..." }
                    ```

                    - 처음 로그인하면 계정이 자동으로 만들어집니다. 별도 회원가입이 없습니다.
                    - 같은 이메일로 가입된 계정이 이미 있으면 연결하지 않고 409 를 줍니다.
                      원래 쓰던 방법으로 로그인하도록 안내해 주세요.
                    - 카카오는 이메일 제공이 선택 동의라, 이메일 없이 가입될 수 있습니다.
                      그 계정은 카카오로만 로그인할 수 있습니다.

                    에러
                    - `UNSUPPORTED_PROVIDER` (400): 지원하지 않거나 서버에 설정되지 않은 공급자
                    - `INVALID_SOCIAL_TOKEN` (401): 서명 · 발급자 · 대상 · 만료 검증 실패
                    - `SOCIAL_EMAIL_CONFLICT` (409): 같은 이메일로 가입된 계정이 이미 있음
                    """)
    @SecurityRequirements
    @PostMapping("/social")
    public ResponseEntity<ApiResponse<AuthResponse>> socialLogin(
            @Valid @RequestBody SocialLoginRequest request) {
        return withRefreshCookie(authService.socialLogin(request));
    }

    @Operation(
            summary = "카카오 로그인 (웹, 인가 코드)",
            description = """
                    웹의 카카오 JS SDK 는 id_token 을 바로 주지 않고, redirect URI 로 **인가 코드**만 넘겨줍니다.
                    그 코드를 보내면 서버가 카카오에 id_token 으로 바꿔 받은 뒤 `/auth/social` 과 같은 절차로 로그인합니다.
                    응답 · 가입 규칙 · 409 는 `/auth/social` 과 같습니다.

                    ```json
                    { "code": "인가 코드", "redirectUri": "https://yacha.com/oauth/kakao" }
                    ```

                    - `redirectUri` 는 `Kakao.Auth.authorize()` 에 넣은 값과 똑같아야 합니다.
                    - 코드는 10분 안에 한 번만 쓸 수 있습니다. 새로고침 등으로 두 번 보내면 두 번째는 401 입니다.
                    - `authorize()` 에 `scope` 를 직접 넘긴다면 `openid` 를 꼭 포함하세요. 빠지면 id_token 이 오지 않습니다.
                    - `state` 로 로그인 CSRF 를 막는 것은 프론트 몫입니다. 콜백에서 확인한 뒤에 이 API 를 호출하세요.

                    에러
                    - `UNSUPPORTED_PROVIDER` (400): 서버에 카카오 REST API 키가 설정되지 않음
                    - `INVALID_SOCIAL_CODE` (401): 코드 만료 · 이미 사용됨 · redirectUri 불일치
                    - `INVALID_SOCIAL_TOKEN` (401): 받은 id_token 검증 실패
                    - `SOCIAL_EMAIL_CONFLICT` (409): 같은 이메일로 가입된 계정이 이미 있음
                    - `SOCIAL_PROVIDER_ERROR` (502): 카카오 서버 오류 · 타임아웃, 또는 서버의 키 설정 오류
                    """)
    @SecurityRequirements
    @PostMapping("/social/kakao")
    public ResponseEntity<ApiResponse<AuthResponse>> kakaoCodeLogin(
            @Valid @RequestBody KakaoCodeLoginRequest request) {
        return withRefreshCookie(kakaoLoginService.login(request));
    }

    @Operation(
            summary = "비밀번호 재설정 메일 요청",
            description = """
                    로그인 화면의 "비밀번호 찾기"
                    입력한 주소로 재설정 링크를 보냄

                    가입되지 않은 이메일이어도 200
                    어떤 주소가 가입돼 있는지 알려주지 않기 위해서임
                    프론트도 "가입된 계정이 있다면 메일을 보냈습니다"처럼 안내해 주세요

                    - 링크는 30분 동안 유효하고 한 번만 사용 가능
                    - 같은 이메일로는 1분에 한 번만 보냄. 제한에 걸려도 응답은 200.
                    - 소셜(카카오 · 구글)로만 가입한 계정은 바꿀 비밀번호가 없어서, 링크 대신
                      "소셜 로그인을 이용하세요" 안내 메일이 갑니다
                    """)
    @SecurityRequirements
    @PostMapping("/password/reset-request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.sendResetMail(request);
        return ApiResponse.noContent();
    }

    @Operation(
            summary = "비밀번호 재설정",
            description = """
                    메일 링크로 들어와 새 비밀번호를 정함
                    링크의 `?token=` 값을 그대로 보내주세요.

                    성공하면 모든 기기에서 로그아웃
                    계정을 도둑맞아 재설정하는 경우 위함
                    새 비밀번호로 다시 로그인하면 됨

                    에러
                    - `VALIDATION_FAILED` (400): 새 비밀번호가 8자 미만 또는 72자 초과
                    - `INVALID_RESET_TOKEN` (401): 링크 만료(30분) · 이미 사용됨 · 잘못된 토큰
                    """)
    @SecurityRequirements
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.reset(request);
        return ApiResponse.noContent();
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

        // 서버 쪽(저장소)과 클라이언트 쪽(쿠키)을 모두 지워야 로그아웃이 끝남
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

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(IssuedTokens tokens) {
        return authResponseFactory.withRefreshCookie(tokens);
    }
}
