package likelion.yacha_backend.domain.session.service;

import likelion.yacha_backend.domain.session.dto.BotMatchRequest;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.service.SessionCommandService.Created;
import likelion.yacha_backend.domain.session.service.SessionCommandService.Matched;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 방 생성 · 입장 · 취소 · 봇전 시작의 진입점. DB 작업은 {@link SessionCommandService} 의 트랜잭션에서 하고,
 * <b>커밋이 끝난 뒤에</b> 대기 타이머 등록 · 정리, 게임 생성 · 구간 타이머 등록, 힌트 생성 시작, 방장 알림을 합니다.
 *
 * <p>트랜잭션 안에서 하면 두 가지 문제가 생깁니다.
 * <ul>
 *   <li>커밋 전에 {@code MATCHED} 를 받은 프론트가 {@code /state} 를 부르면 아직 {@code WAITING} 이 보입니다.</li>
 *   <li>커밋이 실패해 롤백되면 메모리에 게임만 남습니다.</li>
 * </ul>
 * 그래서 이 클래스에는 트랜잭션을 걸지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMatchFacade {

    private final SessionCommandService sessionCommandService;
    private final GameRegistry gameRegistry;
    private final MatchNotifier matchNotifier;
    private final WaitTimerService waitTimerService;
    private final PhaseScheduler phaseScheduler;
    private final HintService hintService;
    private final GameConnectionService gameConnectionService;
    private final SessionProposalService sessionProposalService;

    /**
     * 방 생성 → (커밋) → 대기 타이머 등록 (30초마다 팝업, 5분이면 취소).
     *
     * <p>방은 이미 커밋됐으므로 타이머 등록이 실패해도 생성은 성공으로 응답합니다. 실패로 응답하면 사용자는 방이
     * 생긴 줄 모르고, 다시 만들려다 {@code ALREADY_IN_SESSION} 을 받습니다. 타이머 없이 남은 방은 주기 정리
     * ({@link WaitingSessionRecovery#sweepExpired}) 가 상한 뒤에 취소합니다.
     */
    public Long create(Long userId, SessionCreateRequest request) {
        Created created = sessionCommandService.create(userId, request);
        try {
            waitTimerService.register(created.sessionId(), userId, created.createdAt());
        } catch (RuntimeException e) {
            log.error("[대기 타이머] 등록하지 못했습니다. sessionId={}", created.sessionId(), e);
        }
        return created.sessionId();
    }

    /** 입장 → (커밋) → 대기 타이머 정리 → 게임 생성 → 방장에게 {@code MATCHED}. 입장한 사람은 응답으로 시작을 압니다. */
    public Long join(Long userId, Long sessionId) {
        Matched matched = sessionCommandService.join(userId, sessionId);
        // 구간 타이머도 같은 세션 id 를 쓰므로, 게임을 만들기 전에 대기 타이머부터 지웁니다.
        waitTimerService.cancel(sessionId);
        createGame(matched);
        matchNotifier.matched(matched.hostUserId(), matched.sessionId());
        // 입장했으니 다음 자동 제안은 처음부터 돕니다. 지우지 못해도 TTL 로 사라지고 다음 /proposal 이 덮어씁니다.
        try {
            sessionProposalService.forget(userId);
        } catch (RuntimeException e) {
            log.error("[자동 제안] 순환 위치를 지우지 못했습니다. userId={}", userId, e);
        }
        return matched.sessionId();
    }

    /** 바로 봇전 → (커밋) → 게임 생성. 사람이 한 명뿐이라 {@code MATCHED} 는 보내지 않고 응답으로 시작을 알립니다. */
    public Long startBot(Long userId, BotMatchRequest request) {
        Matched matched = sessionCommandService.startBot(userId, request);
        createGame(matched);
        return matched.sessionId();
    }

    /** 대기 중 AI 전환 → (커밋) → 대기 타이머 정리 → 게임 생성. 전환을 요청한 방장이 응답으로 시작을 압니다. */
    public Long convertToAi(Long userId, Long sessionId) {
        Matched matched = sessionCommandService.convertToAi(userId, sessionId);
        waitTimerService.cancel(sessionId);
        createGame(matched);
        return matched.sessionId();
    }

    /** 대기 취소 → (커밋) → 대기 타이머 정리. */
    public void cancel(Long userId, Long sessionId) {
        sessionCommandService.cancel(userId, sessionId);
        waitTimerService.cancel(sessionId);
    }

    /**
     * 게임을 만들고 시간표(60~260초) 타이머를 등록합니다. 봇전도 같은 시간표입니다. 대기 중에 토론방을 미리
     * 구독해 둔 참가자 연결도 이때 인정합니다 ({@link GameConnectionService#connectSubscribed}).
     *
     * <p>세션은 이미 {@code IN_PROGRESS} 로 커밋됐으므로 여기서 실패해도 입장 · 봇전 · AI 전환은 성공으로 응답합니다
     * ({@link #create} 와 같은 이유). 게임이나 타이머 없이 남은 세션은 주기 정리
     * ({@link InProgressSessionCleaner#sweepOverdue}) 가 시간표가 끝난 뒤에 끝냅니다.
     */
    private void createGame(Matched matched) {
        try {
            gameRegistry.create(matched.sessionId(), matched.startedAt(), matched.participantIdByUserId(),
                    matched.aiParticipantId());
            phaseScheduler.register(matched.sessionId(), matched.startedAt());
        } catch (RuntimeException e) {
            log.error("[게임 생성] 게임 · 시간표를 만들지 못했습니다. sessionId={}", matched.sessionId(), e);
        }
        try {
            gameConnectionService.connectSubscribed(matched.sessionId());
        } catch (RuntimeException e) {
            log.error("[게임 생성] 미리 구독한 연결을 인정하지 못했습니다. sessionId={}", matched.sessionId(), e);
        }
        // 힌트는 시간표와 따로 시작합니다. 힌트가 실패해도 게임은 이어지고, 시간표 등록이 실패해도 힌트는 만듭니다.
        try {
            hintService.start(matched.sessionId());
        } catch (RuntimeException e) {
            log.error("[힌트] 만들기 시작하지 못했습니다. sessionId={}", matched.sessionId(), e);
        }
    }
}
