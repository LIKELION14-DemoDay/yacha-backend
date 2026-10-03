package likelion.yacha_backend.domain.session.game;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import likelion.yacha_backend.global.config.GameSchedulerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 세션별 타이머. 구간 전환 · 이탈 유예 · 대기 팝업처럼 "정해진 시각에 할 일" 을 세션 단위로 등록하고 한 번에 취소합니다.
 *
 * <p>시각은 서버 · DB 와 같은 KST {@link LocalDateTime} 으로 받아 {@link Clock} 의 시간대로 바꿉니다.
 * <b>이미 지난 시각이면 바로 실행</b>합니다. 서버 재시작 뒤 대기 타이머를 다시 등록할 때도 그대로 쓸 수 있습니다.
 *
 * <p>타이머 작업은 실행될 때 <b>DB 상태를 다시 확인</b>하고, 상태 전환은 조건부 UPDATE 로 해야 합니다.
 * 취소와 실행이 겹치면 이미 시작된 작업은 멈추지 않으므로, 작업 쪽에서 "이미 바뀌었으면 아무것도 하지 않음" 을 지켜야 합니다.
 *
 * <p>작업은 짧게 끝나야 합니다. LLM 호출처럼 오래 걸리는 일은 별도 실행기로 넘깁니다 ({@link GameSchedulerConfig}).
 * 작업이 예외를 던져도 잡아서 로그만 남기고, 다른 타이머는 계속 돕니다.
 *
 * <p>끝난 타이머는 목록에서 스스로 빠집니다. 세션의 목록 자체는 {@link #cancelAll} 로 지우므로, 게임이 끝날 때
 * (종료 · 취소) 반드시 {@code cancelAll} 을 부릅니다.
 */
@Slf4j
@Component
public class GameTimers {

    private final TaskScheduler scheduler;
    private final Clock clock;
    private final Map<Long, Set<ScheduledFuture<?>>> futuresBySessionId = new ConcurrentHashMap<>();

    public GameTimers(@Qualifier(GameSchedulerConfig.GAME_TASK_SCHEDULER) TaskScheduler scheduler, Clock clock) {
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /**
     * {@code at} 에 {@code task} 를 실행하도록 등록합니다. {@code at} 이 이미 지났으면 바로 실행합니다.
     *
     * @param at KST 시각 (서버 · DB 와 같은 기준)
     */
    public void schedule(Long sessionId, LocalDateTime at, Runnable task) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(task, "task");
        Instant instant = at.atZone(clock.getZone()).toInstant();
        // 목록을 꺼낸 뒤 등록하기 전에 cancelAll 이 끼어들면, 맵에서 빠진 목록에 등록돼 다시는 취소할 수 없습니다.
        // 그래서 같은 세션의 등록 · 취소를 compute 안에서 하나씩 차례로 처리합니다.
        futuresBySessionId.compute(sessionId, (id, current) -> {
            Set<ScheduledFuture<?>> futures = current != null ? current : ConcurrentHashMap.newKeySet();
            AtomicReference<ScheduledFuture<?>> self = new AtomicReference<>();
            // 지난 시각이면 등록하자마자 실행될 수 있습니다. 목록에 넣기 전에 끝나 버리면 빼지 못하므로,
            // 등록 · 추가를 목록 락 안에서 하고 실행이 끝날 때도 같은 락으로 뺍니다.
            synchronized (futures) {
                ScheduledFuture<?> future = scheduler.schedule(() -> run(sessionId, task, futures, self), instant);
                self.set(future);
                futures.add(future);
            }
            return futures;
        });
    }

    /** 세션의 남은 타이머를 모두 취소하고 목록을 지웁니다. 이미 실행 중인 작업은 멈추지 않습니다. */
    public void cancelAll(Long sessionId) {
        // schedule 과 같은 이유로 compute 안에서 취소하고, null 을 돌려줘 맵에서 뺍니다.
        futuresBySessionId.computeIfPresent(sessionId, (id, futures) -> {
            synchronized (futures) {
                futures.forEach(future -> future.cancel(false));
                futures.clear();
            }
            return null;
        });
    }

    /** 아직 실행되지 않은(또는 실행 중인) 타이머 수. 테스트 확인용입니다. */
    int pendingCount(Long sessionId) {
        Set<ScheduledFuture<?>> futures = futuresBySessionId.get(sessionId);
        if (futures == null) {
            return 0;
        }
        synchronized (futures) {
            return futures.size();
        }
    }

    private void run(Long sessionId, Runnable task, Set<ScheduledFuture<?>> futures,
                     AtomicReference<ScheduledFuture<?>> self) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("[게임 타이머] 작업이 실패했습니다. sessionId={}", sessionId, e);
        } finally {
            synchronized (futures) {
                futures.remove(self.get());
            }
        }
    }
}
