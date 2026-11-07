package likelion.yacha_backend.domain.heart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import likelion.yacha_backend.domain.heart.dto.HeartResponse;
import likelion.yacha_backend.domain.heart.service.HeartService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "하트", description = "무료 하트(생명) — 남은 하트 · 다음 충전 시각")
@RestController
@RequestMapping("/api/v1/hearts")
@RequiredArgsConstructor
public class HeartController {

    private final HeartService heartService;

    @Operation(
            summary = "내 하트",
            description = """
                    남은 하트(`hearts`), 최대 개수(`max`), 다음 하트가 차는 시각(`nextChargeAt`, 가득 차 있으면 null)
                    한 판에 1개를 쓰고, 하나 쓰면 15분마다 1개씩 최대까지 다시 참 (충전은 조회할 때 계산)

                    하트는 게임이 실제로 시작될 때 깎임 — 랜덤 방 · 친구 방 입장(두 사람 모두), 봇전 전환(방장만)
                    방 만들기 · 대기 취소는 깎지 않음. 무효(`ABORTED`)로 끝나거나 판정이 끝내 실패하면 돌려줌, 몰수패는 돌려주지 않음
                    0개면 방 만들기 · 입장이 `HEART_EXHAUSTED`

                    로그인 필요 (게스트 가능)
                    """)
    @GetMapping("/me")
    public ApiResponse<HeartResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(heartService.getMyHearts(authUser.getUserId()));
    }
}
