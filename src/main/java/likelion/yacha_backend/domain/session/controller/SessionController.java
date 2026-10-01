package likelion.yacha_backend.domain.session.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import likelion.yacha_backend.domain.session.dto.GameMessageResponse;
import likelion.yacha_backend.domain.session.dto.SessionStateResponse;
import likelion.yacha_backend.domain.session.service.SessionQueryService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "토론 세션", description = "토론방 조회 — 현재 상태 · 놓친 메시지")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionQueryService sessionQueryService;

    @Operation(
            summary = "현재 상태",
            description = """
                    재접속 · 새로고침 · 관전 입장 때 구간과 남은 시간을 맞춤
                    남은 시간은 `endsAt - serverNow` 로 계산 (시각은 KST, `+09:00`)

                    참가자, 또는 진행 중인 랜덤 사람전의 관전자만 조회 가능
                    친구 방 · 봇전은 참가자만 (`NOT_PARTICIPANT`)
                    """)
    @GetMapping("/{sessionId}/state")
    public ApiResponse<SessionStateResponse> getState(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(sessionQueryService.getState(sessionId, authUser.getUserId()));
    }

    @Operation(
            summary = "메시지 조회",
            description = """
                    `seqNo > afterSeq` 인 메시지를 오름차순으로 (재접속 보충 · 늦게 들어온 관전자는 `afterSeq=0`, 음수는 0 으로 봄)
                    채팅은 서버 메모리에만 있어 게임 중에만 조회됨 (끝난 게임은 `SESSION_NOT_IN_PROGRESS`)

                    볼 수 있는 사람은 현재 상태 조회와 같음
                    """)
    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<GameMessageResponse>> getMessages(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "0") long afterSeq) {
        return ApiResponse.success(sessionQueryService.getMessages(sessionId, authUser.getUserId(), afterSeq));
    }
}
