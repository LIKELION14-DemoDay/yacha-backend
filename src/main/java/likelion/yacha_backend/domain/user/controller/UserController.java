package likelion.yacha_backend.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import likelion.yacha_backend.domain.auth.controller.AuthResponseFactory;
import likelion.yacha_backend.domain.auth.dto.AuthResponse;
import likelion.yacha_backend.domain.auth.service.AuthService;
import likelion.yacha_backend.domain.user.dto.MyInfoResponse;
import likelion.yacha_backend.domain.user.dto.NicknameUpdateRequest;
import likelion.yacha_backend.domain.user.dto.PasswordChangeRequest;
import likelion.yacha_backend.domain.user.service.UserService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "사용자", description = "내 정보 조회 · 닉네임 변경")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    // 비밀번호는 인증 관심사라 AuthService 가 처리 (비밀번호 대조 · 토큰 재발급이 한 흐름)
    private final AuthService authService;
    private final AuthResponseFactory authResponseFactory;

    @Operation(
            summary = "내 정보 조회",
            description = """
                    게스트도 호출 가능
                    게스트는 `email`이 `null`

                    `stats`는 판정 도메인 연동 전까지 0으로 내려감

                    `provider`(LOCAL / KAKAO / GOOGLE)는 가입 경로
                    LOCAL이 아니면 비밀번호가 없는 계정이라, 마이페이지의 "비밀번호 변경" 메뉴를 숨기면 됨
                    """)
    @GetMapping("/me")
    public ApiResponse<MyInfoResponse> getMyInfo(@AuthenticationPrincipal AuthUser authUser) {
        // 사용자 식별은 반드시 토큰에서 꺼냄
        // 경로나 body로 받은 id를 믿으면 남의 정보를 조회할 수 있게 됨
        return ApiResponse.success(userService.getMyInfo(authUser.getUserId()));
    }

    @Operation(
            summary = "닉네임 변경",
            description = """
                    변경된 내 정보를 그대로 돌려줌

                    회원 전용. 게스트는 `GUEST_NOT_ALLOWED` (403)
                    """)
    @PatchMapping("/me")
    public ApiResponse<MyInfoResponse> changeNickname(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody NicknameUpdateRequest request) {
        return ApiResponse.success(userService.changeNickname(authUser.getUserId(), request));
    }

    @Operation(
            summary = "비밀번호 변경",
            description = """
                    현재 비밀번호를 확인한 뒤 새 비밀번호로 변경

                    바꾸면 토큰이 새로 발급되어 다른 기기는 로그아웃됨
                    프론트는 응답의 새 `accessToken` 으로 저장된 토큰을 교체해야 해요!
                    교체하지 않으면 최대 10분 뒤 본인도 로그아웃됩니다

                    비밀번호가 없는 계정(소셜 전용 · 게스트)은 호출할 수 없음
                    `GET /users/me`의 `provider`가 `LOCAL`이고 `isGuest`가 `false`일 때만 메뉴를 노출해 주세요

                    에러
                    - `VALIDATION_FAILED` (400): 새 비밀번호가 8자 미만 또는 72자 초과
                    - `SAME_AS_CURRENT_PASSWORD` (400): 현재 비밀번호와 같음
                    - `CURRENT_PASSWORD_MISMATCH` (401): 현재 비밀번호가 틀림
                    - `GUEST_NOT_ALLOWED` (403): 게스트
                    - `PASSWORD_NOT_SET` (409): 비밀번호로 로그인하는 계정이 아님 (소셜 전용)
                    """)
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PasswordChangeRequest request) {
        return authResponseFactory.withRefreshCookie(
                authService.changePassword(authUser.getUserId(), request));
    }
}
