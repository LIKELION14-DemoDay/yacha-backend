package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import likelion.yacha_backend.domain.session.dto.FinalNoticeEvent;
import likelion.yacha_backend.domain.session.dto.PhaseChangedEvent;
import likelion.yacha_backend.domain.session.dto.SessionFinishedEvent;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.global.util.DateTimes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구간 스케줄러 (명세 2-11). 게임이 시작될 때 시간표의 시각을 모두 {@link GameTimers} 에 등록해,
 * 사용자가 아무것도 보내지 않아도 토론이 흘러가게 합니다.
 *
 * <pre>
 *  60초  PHASE_CHANGED(REVEAL)       63초  주장 공개 (ARGUMENT)
 *  80초  PHASE_CHANGED(REBUTTAL)    140초  PHASE_CHANGED(CHAT)
 * 143초  반론 공개 (ARGUMENT)        230초  FINAL_NOTICE
 * 260초  세션 종료(COMPLETED) → PHASE_CHANGED(JUDGING) · SESSION_FINISHED
 * </pre>
 *
 * <p><b>공개 · 전송은 게임 락 안에서</b> 합니다 ({@link GameMessageService} 와 같은 규칙). 채팅 · 제출 처리도
 * 먼저 공개하므로 둘이 겹쳐도 글은 한 번만 공개되고, 항상 채팅보다 앞 {@code seqNo} 를 받습니다.
 *
 * <p>작업은 실행할 때 게임을 다시 확인하고, 없거나 끝난 게임이면 아무것도 하지 않습니다.
 * 종료는 {@code finishIfInProgress} 조건부 UPDATE 라 나가기 · 끊김 처리와 겹쳐도 한쪽만 끝냅니다.
 *
 * <p>정상 종료된 게임은 판정이 대화 전체를 읽어야 하므로 메모리에서 지우지 않고 쓰기만 막습니다 ({@link Game#finish()}).
 * 판정 시작 · 결과 보관 뒤 정리는 판정 · 결과 작업에서 붙입니다.
 */
@Service
public class PhaseScheduler {

    private final GameTimers gameTimers;
    private final GameRegistry gameRegistry;
    private final GameMessageService gameMessageService;
    private final DebateSessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PhaseScheduler(GameTimers gameTimers, GameRegistry gameRegistry, GameMessageService gameMessageService,
                          DebateSessionRepository sessionRepository, PlatformTransactionManager transactionManager,
                          Clock clock) {
        this.gameTimers = gameTimers;
        this.gameRegistry = gameRegistry;
        this.gameMessageService = gameMessageService;
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 게임의 남은 시간표를 등록합니다. 게임을 만든 직후(커밋 뒤) 부릅니다.
     * 이미 지난 구간 알림은 보내지 않고, 공개 · 종료는 지났어도 등록해 바로 실행합니다 (빠지면 안 되는 일이라서).
     */
    public void register(Long sessionId, LocalDateTime startedAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (DebatePhase phase : new DebatePhase[]{DebatePhase.REVEAL, DebatePhase.REBUTTAL, DebatePhase.CHAT}) {
            LocalDateTime at = phase.startAt(startedAt);
            if (!at.isBefore(now)) {
                gameTimers.schedule(sessionId, at, () -> phaseChanged(sessionId, phase, phase.endAt(startedAt)));
            }
        }
        gameTimers.schedule(sessionId, DebatePhase.PREP.revealAt(startedAt), () -> reveal(sessionId));
        gameTimers.schedule(sessionId, DebatePhase.REBUTTAL.revealAt(startedAt), () -> reveal(sessionId));
        LocalDateTime finalNoticeAt = DebatePhase.finalNoticeAt(startedAt);
        if (!finalNoticeAt.isBefore(now)) {
            gameTimers.schedule(sessionId, finalNoticeAt,
                    () -> finalNotice(sessionId, DebatePhase.CHAT.endAt(startedAt)));
        }
        gameTimers.schedule(sessionId, DebatePhase.JUDGING.startAt(startedAt), () -> finish(sessionId));
    }

    private void phaseChanged(Long sessionId, DebatePhase phase, LocalDateTime endsAt) {
        activeGame(sessionId).ifPresent(game -> {
            synchronized (game) {
                ZoneId zone = clock.getZone();
                gameMessageService.broadcastEvent(sessionId, PhaseChangedEvent.of(phase,
                        DateTimes.withOffset(endsAt, zone), DateTimes.withOffset(LocalDateTime.now(clock), zone)));
            }
        });
    }

    private void reveal(Long sessionId) {
        activeGame(sessionId).ifPresent(game -> {
            synchronized (game) {
                gameMessageService.broadcastAll(sessionId, game.revealArguments(LocalDateTime.now(clock)));
            }
        });
    }

    private void finalNotice(Long sessionId, LocalDateTime chatEndsAt) {
        activeGame(sessionId).ifPresent(game -> {
            synchronized (game) {
                gameMessageService.broadcastEvent(sessionId,
                        FinalNoticeEvent.of(DateTimes.withOffset(chatEndsAt, clock.getZone())));
            }
        });
    }

    /** 260초 — 세션을 끝내고 판정 화면으로 넘깁니다. 이미 다른 이유로 끝났으면 아무것도 하지 않습니다. */
    private void finish(Long sessionId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Integer finished = transactionTemplate.execute(
                status -> sessionRepository.finishIfInProgress(sessionId, FinishReason.COMPLETED, now));
        if (finished == null || finished == 0) {
            return;
        }
        gameTimers.cancelAll(sessionId);
        gameRegistry.find(sessionId).ifPresent(game -> {
            synchronized (game) {
                // 반론 공개(143초)가 아직 안 됐다면(타이머 지연 등) 판정 입력에서 빠지지 않게 마지막으로 공개합니다.
                gameMessageService.broadcastAll(sessionId, game.revealArguments(now));
                game.finish();
                ZoneId zone = clock.getZone();
                gameMessageService.broadcastEvent(sessionId,
                        PhaseChangedEvent.of(DebatePhase.JUDGING, null, DateTimes.withOffset(now, zone)));
                gameMessageService.broadcastEvent(sessionId, SessionFinishedEvent.of(FinishReason.COMPLETED));
            }
        });
    }

    /** 메모리에 있고 아직 끝나지 않은 게임. 없으면 시작 전 · 종료 · 서버 재시작입니다. */
    private Optional<Game> activeGame(Long sessionId) {
        return gameRegistry.find(sessionId).filter(game -> !game.isFinished());
    }
}
