package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@DisplayName("GameTimers — 세션별 타이머")
class GameTimersTest {

    private static final long SESSION = 1L;
    private static final long OTHER_SESSION = 2L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), KST);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private ManualTaskScheduler scheduler;
    private GameTimers timers;

    @BeforeEach
    void setUp() {
        scheduler = new ManualTaskScheduler();
        timers = new GameTimers(scheduler, CLOCK);
    }

    private static Instant instant(LocalDateTime at) {
        return at.atZone(KST).toInstant();
    }

    @Test
    @DisplayName("KST 시각을 그 시각의 Instant 로 등록하고, 그 시각이 되면 실행한다")
    void runsAtTime() {
        AtomicInteger runs = new AtomicInteger();
        LocalDateTime at = NOW.plusSeconds(60);

        timers.schedule(SESSION, at, runs::incrementAndGet);

        assertThat(scheduler.startTimes()).containsExactly(instant(at));
        scheduler.runUntil(instant(at).minusMillis(1));
        assertThat(runs).hasValue(0);
        scheduler.runUntil(instant(at));
        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("이미 지난 시각이면 바로 실행한다 (재시작 뒤 다시 등록할 때, 실제 스케줄러)")
    void runsPastTimeImmediately() throws Exception {
        ThreadPoolTaskScheduler realScheduler = new ThreadPoolTaskScheduler();
        realScheduler.initialize();
        try {
            GameTimers realTimers = new GameTimers(realScheduler, Clock.system(KST));
            CountDownLatch ran = new CountDownLatch(1);

            realTimers.schedule(SESSION, LocalDateTime.now(KST).minusMinutes(3), ran::countDown);

            assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            realScheduler.shutdown();
        }
    }

    @Test
    @DisplayName("cancelAll 하면 그 세션의 남은 타이머는 실행되지 않고, 다른 세션은 영향이 없다")
    void cancelAll() {
        AtomicInteger cancelledRuns = new AtomicInteger();
        AtomicInteger otherRuns = new AtomicInteger();
        timers.schedule(SESSION, NOW.plusSeconds(1), cancelledRuns::incrementAndGet);
        timers.schedule(SESSION, NOW.plusSeconds(2), cancelledRuns::incrementAndGet);
        timers.schedule(OTHER_SESSION, NOW.plusSeconds(1), otherRuns::incrementAndGet);

        timers.cancelAll(SESSION);
        scheduler.runUntil(instant(NOW.plusMinutes(10)));

        assertThat(timers.pendingCount(SESSION)).isZero();
        assertThat(cancelledRuns).hasValue(0);
        assertThat(otherRuns).hasValue(1);
    }

    @Test
    @DisplayName("cancelAll 뒤 같은 세션에 다시 등록하면 새 목록에 들어가 다시 취소할 수 있다")
    void schedulesAgainAfterCancel() {
        AtomicInteger runs = new AtomicInteger();
        timers.schedule(SESSION, NOW.plusSeconds(1), runs::incrementAndGet);
        timers.cancelAll(SESSION);

        timers.schedule(SESSION, NOW.plusSeconds(2), runs::incrementAndGet);
        assertThat(timers.pendingCount(SESSION)).isEqualTo(1);

        timers.cancelAll(SESSION);
        scheduler.runUntil(instant(NOW.plusMinutes(10)));
        assertThat(runs).hasValue(0);
    }

    @Test
    @DisplayName("작업 안에서 같은 세션의 다음 타이머를 등록할 수 있다 (구간 전환이 다음 구간을 거는 경우)")
    void schedulesNextFromTask() {
        AtomicInteger runs = new AtomicInteger();
        timers.schedule(SESSION, NOW.plusSeconds(1), () -> {
            runs.incrementAndGet();
            timers.schedule(SESSION, NOW.plusSeconds(2), runs::incrementAndGet);
        });

        scheduler.runUntil(instant(NOW.plusSeconds(1)));
        assertThat(timers.pendingCount(SESSION)).isEqualTo(1);

        scheduler.runUntil(instant(NOW.plusSeconds(2)));
        assertThat(runs).hasValue(2);
        assertThat(timers.pendingCount(SESSION)).isZero();
    }

    @Test
    @DisplayName("작업이 예외를 던져도 다음 타이머는 실행된다")
    void survivesFailingTask() {
        AtomicInteger next = new AtomicInteger();
        timers.schedule(SESSION, NOW, () -> {
            throw new IllegalStateException("타이머 실패");
        });
        timers.schedule(SESSION, NOW, next::incrementAndGet);

        scheduler.runUntil(instant(NOW));

        assertThat(next).hasValue(1);
        assertThat(timers.pendingCount(SESSION)).isZero();
    }

    @Test
    @DisplayName("끝난 타이머는 목록에서 빠지고, 남은 타이머만 센다")
    void removesFinishedTimers() {
        timers.schedule(SESSION, NOW, () -> { });
        timers.schedule(SESSION, NOW.plusMinutes(10), () -> { });

        scheduler.runUntil(instant(NOW));
        assertThat(timers.pendingCount(SESSION)).isEqualTo(1);

        timers.cancelAll(SESSION);
        assertThat(timers.pendingCount(SESSION)).isZero();
    }

    @Test
    @DisplayName("등록한 적 없는 세션을 cancelAll 해도 예외가 없다")
    void cancelUnknownSession() {
        timers.cancelAll(404L);

        assertThat(timers.pendingCount(404L)).isZero();
    }

    /** 등록만 받아 두고, 테스트가 {@link #runUntil} 로 시각을 정해 직접 실행하는 스케줄러 */
    private static final class ManualTaskScheduler implements TaskScheduler {

        private final List<ManualTask> tasks = new ArrayList<>();

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            ManualTask scheduled = new ManualTask(task, startTime);
            tasks.add(scheduled);
            return scheduled;
        }

        List<Instant> startTimes() {
            return tasks.stream().map(task -> task.startTime).toList();
        }

        /** {@code now} 까지 예정된 작업을 등록 순서대로 실행. 취소됐거나 이미 실행한 작업은 건너뜀 (FutureTask) */
        void runUntil(Instant now) {
            for (int i = 0; i < tasks.size(); i++) {
                ManualTask task = tasks.get(i);
                if (!task.startTime.isAfter(now)) {
                    task.run();
                }
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ManualTask extends FutureTask<Void> implements ScheduledFuture<Void> {

        private final Instant startTime;

        ManualTask(Runnable task, Instant startTime) {
            super(task, null);
            this.startTime = startTime;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(Delayed other) {
            throw new UnsupportedOperationException();
        }
    }
}
