package likelion.yacha_backend.domain.session.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import likelion.yacha_backend.domain.session.dto.GameMessageResponse;
import likelion.yacha_backend.domain.session.dto.MemoResponse;
import likelion.yacha_backend.domain.session.dto.MemoSaveRequest;
import likelion.yacha_backend.domain.session.dto.SessionStateResponse;
import likelion.yacha_backend.domain.session.service.GameMemoService;
import likelion.yacha_backend.domain.session.service.SessionQueryService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "토론 세션", description = "토론방 조회 — 현재 상태 · 놓친 메시지 · 내 주장 작성")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionQueryService sessionQueryService;
    private final GameMemoService gameMemoService;

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

    @Operation(
            summary = "주장 · 반론 제출",
            description = """
                    `PREP` 에는 주장(200자), `REBUTTAL` 에는 반론(250자)을 제출 (구간당 하나, 다시 제출하면 덮어씀)
                    제출하면 토론방에 `ARGUMENT_SUBMITTED` 알림만 가고(내용 없음), 공개 전까지 내용은 본인만 봄
                    작성 구간이 끝나고 3초 뒤(주장 63초, 반론 143초) `ARGUMENT` 메시지로 양쪽에 공개됨. 빈 글은 공개되지 않음

                    시간이 끝나면 프론트가 쓰던 글을 바로 제출(자동 제출) — 서버는 구간 종료 뒤 3초까지 받음
                    참가자만 (`NOT_PARTICIPANT`), 작성 구간(유예 포함)이 아니거나 이미 공개됐으면 `INVALID_PHASE`,
                    글자 수 초과면 `CONTENT_TOO_LONG`, 게임 중이 아니면 `SESSION_NOT_IN_PROGRESS`
                    """)
    @PutMapping("/{sessionId}/memo")
    public ApiResponse<MemoResponse> saveMemo(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId,
            @RequestBody MemoSaveRequest request) {
        return ApiResponse.success(gameMemoService.saveMemo(sessionId, authUser.getUserId(), request.content()));
    }

    @Operation(
            summary = "내 주장 · 반론 조회",
            description = """
                    내가 제출한 주장 · 반론을 작성 구간 순서대로 (새로고침 때 복구용)
                    참가자만 (`NOT_PARTICIPANT`) — 상대 · 관전자가 공개 전 글을 보는 방법은 없음
                    게임 중에만 조회됨 (`SESSION_NOT_IN_PROGRESS`)
                    """)
    @GetMapping("/{sessionId}/memo")
    public ApiResponse<List<MemoResponse>> getMyMemos(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(gameMemoService.getMyMemos(sessionId, authUser.getUserId()));
    }
}
