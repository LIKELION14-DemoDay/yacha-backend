package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.session.dto.SessionFinishedEvent;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateResult;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게임 중 종료 — 나가기 · 연결 끊김 몰수패(명세 2-11)와 양쪽 동시 이탈 무효.
 *
 * <pre>
 * 나가기 (POST /leave)     ─┐
 * 이탈 유예 10초 경과       ─┴▶ forfeit: FINISHED · FORFEIT, 나간 사람 LOSE · 남은 사람 WIN (판정 없음)
 * 유예가 끝날 때 상대도 끊김 ─┐
 * 봇이 글을 끝내 못 씀       ─┴▶ abort  : FINISHED · ABORTED, 승패 NULL
 * </pre>
 *
 * <p>종료는 {@code finishIfInProgress} 조건부 UPDATE 라 260초 종료 · 나가기 · 끊김 몰수가 겹쳐도 한쪽만 끝냅니다.
 * 승패는 같은 트랜잭션에서 기록하고, 규칙은 판정과 같습니다 ({@link DebateParticipant#keepsRecord()}).
 *
 * <p>끝나면 남은 타이머를 취소하고 게임을 바로 메모리에서 지운 뒤 {@code SESSION_FINISHED} 를 보냅니다.
 * 몰수패 · 무효는 판정이 없어 대화를 남길 이유가 없습니다 (명세 1-5). 정리를 전송보다 먼저 해서 전송이 실패해도
 * 게임이 메모리에 남지 않습니다.
 */
@Service
public class GameExitService {

    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final GameRegistry gameRegistry;
    private final GameTimers gameTimers;
    private final GameMessageService gameMessageService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public GameExitService(DebateSessionRepository sessionRepository, DebateParticipantRepository participantRepository,
                           GameRegistry gameRegistry, GameTimers gameTimers, GameMessageService gameMessageService,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.gameRegistry = gameRegistry;
        this.gameTimers = gameTimers;
        this.gameMessageService = gameMessageService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 게임 중 나가기. 유예 없이 바로 몰수패로 끝냅니다.
     *
     * @throws BusinessException {@code SESSION_NOT_FOUND} 없는 세션, {@code NOT_PARTICIPANT} 참가자가 아님,
     *                           {@code SESSION_NOT_IN_PROGRESS} 대기 중이거나 이미 끝남
     */
    public void leave(Long userId, Long sessionId) {
        Long leaverId = transactionTemplate.execute(status -> {
            if (!sessionRepository.existsById(sessionId)) {
                throw new BusinessException(SessionErrorCode.SESSION_NOT_FOUND);
            }
            return participantRepository.findAllBySession_Id(sessionId).stream()
                    .filter(participant -> participant.isUser(userId))
                    .findFirst()
                    .map(DebateParticipant::getId)
                    .orElseThrow(() -> new BusinessException(SessionErrorCode.NOT_PARTICIPANT));
        });
        if (!forfeit(sessionId, leaverId)) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_IN_PROGRESS);
        }
    }

    /**
     * 몰수패로 끝냅니다. 진 사람 {@code LOSE}, 나머지 {@code WIN}. 이미 끝난 세션이면 아무것도 하지 않고 false 입니다.
     *
     * @param loserParticipantId 나가거나 끊긴 참가자
     */
    boolean forfeit(Long sessionId, Long loserParticipantId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Boolean finished = transactionTemplate.execute(status -> {
            if (sessionRepository.finishIfInProgress(sessionId, FinishReason.FORFEIT, now) == 0) {
                return false;
            }
            recordForfeit(sessionId, loserParticipantId);
            return true;
        });
        if (!Boolean.TRUE.equals(finished)) {
            return false;
        }
        endGame(sessionId, FinishReason.FORFEIT);
        return true;
    }

    /**
     * 게임을 무효로 끝냅니다 — 양쪽 모두 끊김 · 봇전에서 봇이 글을 끝내 못 씀 ({@link BotService}).
     * 승패는 NULL 입니다. 이미 끝난 세션이면 false 입니다.
     */
    boolean abort(Long sessionId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Integer finished = transactionTemplate.execute(
                status -> sessionRepository.finishIfInProgress(sessionId, FinishReason.ABORTED, now));
        if (finished == null || finished == 0) {
            return false;
        }
        endGame(sessionId, FinishReason.ABORTED);
        return true;
    }

    private void recordForfeit(Long sessionId, Long loserParticipantId) {
        for (DebateParticipant participant : participantRepository.findAllBySession_Id(sessionId)) {
            if (participant.getResult() != null || !participant.keepsRecord()) {
                continue;
            }
            participant.recordResult(
                    participant.getId().equals(loserParticipantId) ? DebateResult.LOSE : DebateResult.WIN);
        }
    }

    private void endGame(Long sessionId, FinishReason reason) {
        gameTimers.cancelAll(sessionId);
        gameRegistry.remove(sessionId);
        gameMessageService.broadcastEvent(sessionId, SessionFinishedEvent.of(reason));
    }
}
