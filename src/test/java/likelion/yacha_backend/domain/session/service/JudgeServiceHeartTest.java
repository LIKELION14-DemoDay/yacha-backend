package likelion.yacha_backend.domain.session.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.Executor;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.domain.heart.service.HeartService;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 판정 실패 때 하트 돌려주기 — 실제 실행기 · 타이머로는 만들기 어려운 경우를 모의 객체로 확인합니다.
 * 실제 흐름은 {@code HeartRefundTest} 가 확인합니다.
 */
@DisplayName("JudgeService — 판정 실패 때 하트 돌려주기")
class JudgeServiceHeartTest {

    private static final Long SESSION_ID = 1L;
    private static final int MAX_RUNS = 3;

    private final GameRegistry gameRegistry = mock(GameRegistry.class);
    private final GameTimers gameTimers = mock(GameTimers.class);
    private final HeartService heartService = mock(HeartService.class);
    private final Game game = mock(Game.class);

    /** 판정 대기열이 가득 차 작업을 받지 않는 실행기 */
    private final Executor rejecting = command -> {
        throw new TaskRejectedException("판정 대기열이 가득 참");
    };

    private final JudgeService judgeService = new JudgeService(gameRegistry, gameTimers, mock(Judge.class),
            mock(DebateSessionRepository.class), mock(DebateParticipantRepository.class),
            mock(PlatformTransactionManager.class), rejecting,
            new JudgeProperties(3, Duration.ofMillis(10), MAX_RUNS - 1, Duration.ofSeconds(2)), heartService,
            Clock.fixed(Instant.parse("2026-11-06T03:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    @DisplayName("마지막 재판정을 실행기가 받지 못하면 바로 돌려준다")
    void refundsWhenLastRunRejected() {
        when(gameRegistry.find(SESSION_ID)).thenReturn(Optional.of(game));
        when(game.startJudging(MAX_RUNS)).thenReturn(true);
        when(game.hasJudgeRunsLeft(MAX_RUNS)).thenReturn(false);

        judgeService.start(SESSION_ID);

        verify(game).failJudging();
        verify(heartService).refund(SESSION_ID);
    }

    @Test
    @DisplayName("재판정이 남았으면 실행기가 받지 못해도 바로 돌려주지 않는다")
    void keepsHeartsWhenRunsLeft() {
        when(gameRegistry.find(SESSION_ID)).thenReturn(Optional.of(game));
        when(game.startJudging(MAX_RUNS)).thenReturn(true);
        when(game.hasJudgeRunsLeft(MAX_RUNS)).thenReturn(true);

        judgeService.start(SESSION_ID);

        verify(game).failJudging();
        verify(heartService, never()).refund(SESSION_ID);
    }
}
