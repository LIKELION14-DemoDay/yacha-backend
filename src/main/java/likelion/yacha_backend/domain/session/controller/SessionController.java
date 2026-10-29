package likelion.yacha_backend.domain.session.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import likelion.yacha_backend.domain.session.dto.HintResponse;
import likelion.yacha_backend.domain.session.dto.BotMatchRequest;
import likelion.yacha_backend.domain.session.dto.CurrentSessionResponse;
import likelion.yacha_backend.domain.session.dto.GameMessageResponse;
import likelion.yacha_backend.domain.session.dto.MemoResponse;
import likelion.yacha_backend.domain.session.dto.MemoSaveRequest;
import likelion.yacha_backend.domain.session.dto.ProposalResponse;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.dto.SessionIdResponse;
import likelion.yacha_backend.domain.session.dto.SessionResultResponse;
import likelion.yacha_backend.domain.session.dto.SessionStateResponse;
import likelion.yacha_backend.domain.session.dto.WaitingRoomsResponse;
import likelion.yacha_backend.domain.session.service.GameExitService;
import likelion.yacha_backend.domain.session.service.GameMemoService;
import likelion.yacha_backend.domain.session.service.HintService;
import likelion.yacha_backend.domain.session.service.SessionMatchFacade;
import likelion.yacha_backend.domain.session.service.SessionProposalService;
import likelion.yacha_backend.domain.session.service.SessionQueryService;
import likelion.yacha_backend.domain.session.service.SessionResultService;
import likelion.yacha_backend.domain.topic.entity.Category;
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

@Tag(name = "토론 세션", description = "방 생성 · 자동 제안 · 방 찾기 · 입장 · 취소 · 봇전, 토론방 조회 — 현재 상태 · 놓친 메시지 · 내 주장 작성 · 결과")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionMatchFacade sessionMatchFacade;
    private final SessionProposalService sessionProposalService;
    private final SessionQueryService sessionQueryService;
    private final GameMemoService gameMemoService;
    private final SessionResultService sessionResultService;
    private final HintService hintService;
    private final GameExitService gameExitService;

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
            summary = "자동 제안",
            description = """
                    "랜덤 주제로 시작하기". 고른 카테고리부터 8개 카테고리를 순서대로 돌며 대기 방을 하나씩 제안
                    (인간 → 관계 → 윤리 → 사회 → 삶과 죽음 → 기술과 미래 → 돈과 성공 → 진실과 믿음 → 처음으로)

                    - `PROPOSAL`: 그 카테고리에서 가장 오래 기다린 방. `myStance` 는 들어가면 맡을 입장(방장의 반대)
                      승낙은 `POST /sessions/{id}/join`, 거절은 `POST /sessions/{id}/reject`
                      제안된 방의 카테고리가 고른 카테고리와 달라도 그 방의 주제로 토론
                    - `NEW_TOPIC`: 한 바퀴를 다 돌았거나 대기 방이 없음. 고른 카테고리의 새 주제 → 주제 화면(내 방 만들기 · 봇전)
                    - 다시 부르면 처음부터 다시 돎. 방을 잡아 두지 않으므로, 입장이 `SESSION_NOT_WAITING` 이면 다시 부르면 됨

                    로그인 필요 (게스트 가능)
                    카테고리 값이 잘못되면 `BINDING_ERROR`, `NEW_TOPIC` 인데 뽑을 주제가 없으면 `TOPIC_NOT_FOUND`
                    """)
    @GetMapping("/proposal")
    public ApiResponse<ProposalResponse> propose(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam Category category) {
        return ApiResponse.success(sessionProposalService.propose(authUser.getUserId(), category));
    }

    @Operation(
            summary = "제안 거절",
            description = """
                    제안받은 방을 거절하고 다음 카테고리의 방을 받음. 응답은 자동 제안과 같음 (`PROPOSAL` / `NEW_TOPIC`)
                    한 바퀴를 다 돌았으면 `NEW_TOPIC`. 10분 넘게 지나 위치가 사라졌으면 거절한 방의 카테고리 다음부터 새로 돎

                    로그인 필요 (게스트 가능)
                    없는 방이면 `SESSION_NOT_FOUND`
                    """)
    @PostMapping("/{sessionId}/reject")
    public ApiResponse<ProposalResponse> reject(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(sessionProposalService.reject(authUser.getUserId(), sessionId));
    }

    @Operation(
            summary = "방 찾기",
            description = """
                    "야차 상대 찾기". 카테고리의 대기 방을 오래 기다린 순으로 한 페이지 20개씩 (`page` 는 0 부터)
                    자동 제안과 같은 대기열이고, 내 방은 빠짐. 입장은 `POST /sessions/{id}/join`

                    카드: 방장(닉네임 · 철학자 유형) · 주제 · `wantedStance`(찾는 입장 = 들어가면 맡을 입장)
                    `philosopherType` 은 방장의 밸런스 게임 결과 (안 했으면 null = "일반인"). `hasNext` 가 true 면 다음 페이지가 있음

                    로그인 필요 (게스트 가능)
                    카테고리 값이 잘못되면 `BINDING_ERROR`, `page` 가 음수면 `VALIDATION_FAILED`
                    """)
    @GetMapping("/waiting")
    public ApiResponse<WaitingRoomsResponse> findWaiting(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam Category category,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(sessionProposalService.findWaiting(authUser.getUserId(), category, page));
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
            summary = "바로 봇전 (NEW_TOPIC 화면 전용)",
            description = """
                    방을 만들지 않고 봇과 바로 토론을 시작함 (`IN_PROGRESS`)

                    언제 부르나: 자동 제안 · 제안 거절에서 `NEW_TOPIC` 을 받은 뒤(한 바퀴를 다 돌았거나 대기 방이 없음), 새 주제 화면에서 "봇전으로 시작" 을 누를 때만
                    - 카테고리 화면에서 바로 봇전을 시작하는 길은 없음
                    - 내 방에서 기다리다 봇전으로 바꾸는 것은 이 API 가 아니라 `POST /sessions/{id}/ai`

                    요청
                    - `topicId`: 새 주제 화면에 떠 있는 주제의 id (`NEW_TOPIC` 응답의 `topic.id`, 다음 주제로 넘겼으면 지금 화면의 주제)
                    - `stance`: 사용자가 고른 입장. 봇은 반대 입장

                    응답을 받은 뒤
                    - 응답의 `data` 는 `{ sessionId }`. 상대가 봇이라 `MATCHED` 알림은 오지 않음
                    - 바로 토론방 `/topic/sessions/{sessionId}` 를 구독하고 `GET /sessions/{sessionId}/state` 로 구간 · 남은 시간을 맞춤

                    로그인 필요 (게스트 가능)
                    이미 대기 · 진행 중인 토론이 있으면 `ALREADY_IN_SESSION`, 없거나 내린 주제면 `TOPIC_NOT_FOUND`
                    """)
    @PostMapping("/bot")
    public ApiResponse<SessionIdResponse> startBot(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody BotMatchRequest request) {
        return ApiResponse.success(new SessionIdResponse(sessionMatchFacade.startBot(authUser.getUserId(), request)));
    }

    @Operation(
            summary = "AI 대결로 전환",
            description = """
                    기다리던 내 방을 봇전으로 바꿔 바로 시작 (`IN_PROGRESS`). 대기 팝업(`WAIT_PROMPT`)에서 "봇전" 을 고르면 부름
                    내 입장은 그대로, 봇은 반대. 즉시 대기열에서 빠짐. 응답을 받으면 토론방을 구독하고 `/state` 로 구간을 맞춤

                    방장만 (`NOT_ROOM_OWNER`), 그 사이 상대가 들어왔거나 취소된 방이면 `SESSION_NOT_WAITING`
                    """)
    @PostMapping("/{sessionId}/ai")
    public ApiResponse<SessionIdResponse> convertToAi(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(new SessionIdResponse(sessionMatchFacade.convertToAi(authUser.getUserId(), sessionId)));
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
            summary = "내 힌트",
            description = """
                    주장 작성 화면의 "힌트 보기" 에 쓸, 내 입장에 맞는 20자 이내 문장 3개
                    매칭 직후 서버가 만들기 시작함. 만드는 중이면 `{ "status": "PENDING" }` (1.5초마다 다시 조회),
                    끝나면 `READY` + `hints`, 만들지 못했으면 `FAILED` (힌트 없이 진행, 다시 만들지 않음)
                    `READY` · `FAILED` 가 오면 조회를 멈춤. `FAILED` 면 "힌트 보기" 버튼을 비활성화하고 "힌트 불러오기를 실패했어요" 로 표시

                    참가자 본인 것만 (`NOT_PARTICIPANT`), 게임 중에만 (`SESSION_NOT_IN_PROGRESS`)
                    """)
    @GetMapping("/{sessionId}/hints")
    public ApiResponse<HintResponse> getMyHints(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(hintService.getMyHints(sessionId, authUser.getUserId()));
    }

    @Operation(
            summary = "게임 중 나가기",
            description = """
                    진행 중인 토론에서 나감. 유예 없이 바로 몰수패 (`FINISHED` · `FORFEIT`)
                    나간 사람 `LOSE`, 남은 사람 `WIN` (판정 없음, 게스트는 승패 기록 안 함). 남은 사람에게 `SESSION_FINISHED { reason: FORFEIT }`
                    나간 사람은 홈 화면으로. 결과는 `GET /sessions/{id}/result` 로 승패만 볼 수 있음

                    참가자만 (`NOT_PARTICIPANT`), 대기 중이거나 이미 끝난 토론이면 `SESSION_NOT_IN_PROGRESS` (대기 취소는 `DELETE /sessions/{id}`)
                    """)
    @PostMapping("/{sessionId}/leave")
    public ApiResponse<SessionIdResponse> leave(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        gameExitService.leave(authUser.getUserId(), sessionId);
        return ApiResponse.success(new SessionIdResponse(sessionId));
    }

    @Operation(
            summary = "내가 참여 중인 세션",
            description = """
                    새로고침 · 재접속으로 sessionId 를 잃었을 때 되찾음. 앱 진입 · 새로고침 때 한 번 부르면 됨
                    `WAITING` 이면 대기 화면으로 돌아가거나 `DELETE /sessions/{id}` 로 취소, `IN_PROGRESS` 면 토론방 구독 후 `/state`
                    대기 · 진행 중인 세션이 없으면 `data: null`

                    로그인 필요 (게스트 가능)
                    """)
    @GetMapping("/current")
    public ApiResponse<CurrentSessionResponse> getCurrent(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(sessionQueryService.getCurrent(authUser.getUserId()));
    }

    @Operation(
            summary = "현재 상태",
            description = """
                    재접속 · 새로고침 때 구간과 남은 시간을 맞춤
                    남은 시간은 `endsAt - serverNow` 로 계산 (시각은 KST, `+09:00`)

                    참가자마다 `nickname` · `philosopherType` · `stanceText` 를 줌
                    - 로딩 화면(매칭 → 채팅방)은 양쪽 각자 `stanceText` (자기 유형 말투의 찬성 / 반대 문구)
                    - 채팅은 `senderId` 로 참가자를 찾아 프로필(유형 이미지) · 닉네임 · 입장을 붙임
                    - 유형이 없으면(일반인) `philosopherType` null, 문구는 "찬성해요" / "반대해요"
                    - 봇은 `nickname` "야차봇" · `philosopherType` null, 문구는 "계산 결과, 찬성입니다" / "계산 결과, 반대입니다"
                    - 계정이 정리된 사람 참가자(게스트 정리 · 가입 뒤 끝난 게임)는 `nickname` · `philosopherType` null, 문구는 일반인 문구
                    - 입장이 없으면(친구 방에서 친구가 들어오기 전) `stanceText` null

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

    @Operation(
            summary = "결과 조회",
            description = """
                    260초에 채팅이 끝나면(`SESSION_FINISHED`) 판정 화면에서 1.5초마다 부름
                    판정 중이면 `{ "status": "PENDING" }`, 끝나면 `READY` + 승패 · 기준 4개(논리 · 근거 · 반박 · 일관성) 점수 · 기준별 한 줄 요약
                    총점이 높은 쪽이 승리, 같으면 둘 다 `DRAW` (`winnerParticipantId` null)

                    판정이 실패하면 다시 부를 때 판정을 다시 시작하고 `PENDING`
                    점수 · 요약은 10분 동안만 있고, 그 뒤에는 승패만 (`criteria` · `scores` · `total` null, 게스트는 승패도 없음)
                    결과를 낼 수 없으면 `FAILED` (판정 중 서버 재시작 · 무효 게임 `ABORTED`)
                    `philosopherType` 은 밸런스 게임 결과 (안 했으면 null = "일반인"), 봇은 `nickname` "야차봇" · `philosopherType` null, 계정이 정리된 사람 참가자는 `nickname` · `philosopherType` null

                    참가자만 (`NOT_PARTICIPANT`), 아직 끝나지 않은 토론이면 `SESSION_NOT_FINISHED`
                    """)
    @GetMapping("/{sessionId}/result")
    public ApiResponse<SessionResultResponse> getResult(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success(sessionResultService.getResult(sessionId, authUser.getUserId()));
    }
}
