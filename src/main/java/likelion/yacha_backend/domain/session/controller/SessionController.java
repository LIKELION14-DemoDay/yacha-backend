package likelion.yacha_backend.domain.session.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import likelion.yacha_backend.domain.session.dto.GameMessageResponse;
import likelion.yacha_backend.domain.session.dto.MemoResponse;
import likelion.yacha_backend.domain.session.dto.MemoSaveRequest;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.dto.SessionIdResponse;
import likelion.yacha_backend.domain.session.dto.SessionStateResponse;
import likelion.yacha_backend.domain.session.service.GameMemoService;
import likelion.yacha_backend.domain.session.service.SessionMatchFacade;
import likelion.yacha_backend.domain.session.service.SessionQueryService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "토론 세션", description = "방 생성 · 입장 · 취소, 토론방 조회 — 현재 상태 · 놓친 메시지 · 내 주장 작성")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionMatchFacade sessionMatchFacade;
    private final SessionQueryService sessionQueryService;
    private final GameMemoService gameMemoService;

    @Operation(
            summary = "방 생성",
            description = """
                    랜덤 방을 만들고 상대를 기다림 (`WAITING`). 친구 방(`FRIEND`)은 구현 보류라 400
                    `topicId` 는 주제 화면에서 받은 주제, `stance` 는 방장의 입장 (들어오는 사람은 반대)
                    방의 카테고리는 주제의 카테고리

                    상대가 들어오면 `/user/queue/match` 로 `MATCHED` 가 옴 (방장은 승낙 없이 자동 수락)
                    로그인 필요 (게스트 가능)
                    이미 대기 · 진행 중인 토론이 있으면 `ALREADY_IN_SESSION`, 없거나 내린 주제면 `TOPIC_NOT_FOUND`
                    """)
    @PostMapping
    public ApiResponse<SessionIdResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody SessionCreateRequest request) {
        return ApiResponse.success(new SessionIdResponse(sessionMatchFacade.create(authUser.getUserId(), request)));
    }

    @Operation(
            summary = "입장",
            description = """
                    대기 중인 랜덤 방에 방장의 반대 입장으로 들어가 바로 시작 (`IN_PROGRESS`)
                    자동 제안 승낙 · 방 찾기 입장 공통. 응답을 받으면 토론방을 구독하고 `/state` 로 구간을 맞춤

                    동시에 들어오면 먼저 들어온 사람만 성공하고, 늦은 사람은 `SESSION_NOT_WAITING` (다시 제안을 요청)
                    로그인 필요 (게스트 가능)
                    내 방이면 `CANNOT_JOIN_OWN_ROOM`, 이미 대기 · 진행 중인 토론이 있으면 `ALREADY_IN_SESSION`
                    """)
    @PostMapping("/{sessionId}/join")
    public ApiResponse<SessionIdResponse> join(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(new SessionIdResponse(sessionMatchFacade.join(authUser.getUserId(), sessionId)));
    }

    @Operation(
            summary = "대기 취소",
            description = """
                    방장이 기다리던 방을 취소 (`CANCELLED`). 응답은 생성 · 입장과 같은 `{ sessionId }`

                    방장만 (`NOT_ROOM_OWNER`), 이미 누가 들어왔거나 취소된 방이면 `SESSION_NOT_WAITING`
                    """)
    @DeleteMapping("/{sessionId}")
    public ApiResponse<SessionIdResponse> cancel(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        sessionMatchFacade.cancel(authUser.getUserId(), sessionId);
        return ApiResponse.success(new SessionIdResponse(sessionId));
    }

    @Operation(
            summary = "현재 상태",
            description = """
                    재접속 · 새로고침 때 구간과 남은 시간을 맞춤
                    남은 시간은 `endsAt - serverNow` 로 계산 (시각은 KST, `+09:00`)

                    참가자만 조회 가능 (`NOT_PARTICIPANT`)
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
                    `seqNo > afterSeq` 인 메시지를 오름차순으로 (재접속 보충, 처음부터 받으려면 `afterSeq=0`, 음수는 0 으로 봄)
                    채팅은 서버 메모리에만 있어 게임 중에만 조회됨 (끝난 게임은 `SESSION_NOT_IN_PROGRESS`)

                    참가자만 조회 가능 (`NOT_PARTICIPANT`)
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
                    참가자만 (`NOT_PARTICIPANT`) — 상대가 공개 전 글을 보는 방법은 없음
                    게임 중에만 조회됨 (`SESSION_NOT_IN_PROGRESS`)
                    """)
    @GetMapping("/{sessionId}/memo")
    public ApiResponse<List<MemoResponse>> getMyMemos(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(gameMemoService.getMyMemos(sessionId, authUser.getUserId()));
    }
}
