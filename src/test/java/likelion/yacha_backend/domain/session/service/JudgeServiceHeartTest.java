package likelion.yacha_backend.domain.session.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.domain.heart.service.HeartService;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.game.JudgeStatus;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    /** 판정 실패 뒤 건 첫 삭제 타이머 작업. 실행기가 거절해도 삭제 타이머는 겁니다. */
    private Runnable removalTask() {
        return removalTasks(1).get(0);
    }

    /** 지금까지 건 삭제 타이머 작업 {@code count} 개를 건 순서대로. */
    private List<Runnable> removalTasks(int count) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(gameTimers, times(count)).schedule(eq(SESSION_ID), any(), task.capture());
        return task.getAllValues();
    }

    private Runnable failedWithRunsLeft() {
        when(gameRegistry.find(SESSION_ID)).thenReturn(Optional.of(game));
        when(game.startJudging(MAX_RUNS)).thenReturn(true);
        when(game.hasJudgeRunsLeft(MAX_RUNS)).thenReturn(true);
        judgeService.start(SESSION_ID);
        return removalTask();
    }

    @Test
    @DisplayName("보관 시간이 끝날 때 판정 실패면 판정을 닫고 돌려준 뒤 지운다")
    void refundsOnRemoval() {
        Runnable removal = failedWithRunsLeft();
        when(game.closeJudging()).thenReturn(true);
        when(game.judgeStatus()).thenReturn(JudgeStatus.FAILED);

        removal.run();

        verify(heartService).refund(SESSION_ID);
        verify(gameRegistry).remove(SESSION_ID);
    }

    @Test
    @DisplayName("보관 시간이 끝나는 순간 재판정이 막 시작됐으면(닫지 못함) 돌려주지도 지우지도 않는다")
    void skipsRemovalWhenRejudgeStarted() {
        Runnable removal = failedWithRunsLeft();
        when(game.closeJudging()).thenReturn(false);

        removal.run();

        verify(heartService, never()).refund(SESSION_ID);
        verify(gameRegistry, never()).remove(SESSION_ID);
    }

    @Test
    @DisplayName("지울 때 돌려주기가 실패하면 게임을 남겨 두고 보관 시간 뒤 다시 시도한다")
    void retriesFailedRefund() {
        Runnable removal = failedWithRunsLeft();
        when(game.closeJudging()).thenReturn(true);
        when(game.judgeStatus()).thenReturn(JudgeStatus.FAILED);
        doThrow(new IllegalStateException("DB 연결 끊김")).doNothing().when(heartService).refund(SESSION_ID);

        removal.run();

        verify(gameRegistry, never()).remove(SESSION_ID);
        Runnable retry = removalTasks(2).get(1);

        retry.run();

        verify(heartService, times(2)).refund(SESSION_ID);
        verify(gameRegistry).remove(SESSION_ID);
    }

    @Test
    @DisplayName("돌려주기가 최대 횟수만큼 모두 실패하면 로그를 남기고 지운다")
    void removesAfterMaxRefundAttempts() {
        Runnable task = failedWithRunsLeft();
        when(game.closeJudging()).thenReturn(true);
        when(game.judgeStatus()).thenReturn(JudgeStatus.FAILED);
        doThrow(new IllegalStateException("DB 연결 끊김")).when(heartService).refund(SESSION_ID);

        for (int attempt = 1; attempt <= JudgeService.REFUND_MAX_ATTEMPTS; attempt++) {
            task.run();
            if (attempt < JudgeService.REFUND_MAX_ATTEMPTS) {
                verify(gameRegistry, never()).remove(SESSION_ID);
                task = removalTasks(attempt + 1).get(attempt);
            }
        }

        verify(heartService, times(JudgeService.REFUND_MAX_ATTEMPTS)).refund(SESSION_ID);
        verify(gameRegistry).remove(SESSION_ID);
    }
}
