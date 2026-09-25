package likelion.yacha_backend.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import likelion.yacha_backend.domain.user.dto.MyInfoResponse;
import likelion.yacha_backend.domain.user.dto.NicknameUpdateRequest;
import likelion.yacha_backend.domain.user.service.UserService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
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

    @Operation(
            summary = "내 정보 조회",
            description = """
                    게스트도 호출 가능 
                    게스트는 `email`이 `null`

                    `stats`는 판정 도메인 연동 전까지 0으로 내려감
                    """)
    @GetMapping("/me")
    public ApiResponse<MyInfoResponse> getMyInfo(@AuthenticationPrincipal AuthUser authUser) {
        // 사용자 식별은 반드시 토큰에서 꺼냄
        // 경로나 body로 받은 id를 믿으면 남의 정보를 조회할 수 있게 됨
        return ApiResponse.success(userService.getMyInfo(authUser.getUserId()));
    }

    @Operation(summary = "닉네임 변경", description = "변경된 내 정보를 그대로 돌려줌")
    @PatchMapping("/me")
    public ApiResponse<MyInfoResponse> changeNickname(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody NicknameUpdateRequest request) {
        return ApiResponse.success(userService.changeNickname(authUser.getUserId(), request));
    }
}
