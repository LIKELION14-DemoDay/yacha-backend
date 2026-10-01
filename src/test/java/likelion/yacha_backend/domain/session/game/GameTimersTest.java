package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@DisplayName("GameTimers — 세션별 타이머")
class GameTimersTest {

    private static final long SESSION = 1L;
    private static final long OTHER_SESSION = 2L;
    /** 실행되지 않아야 할 타이머를 기다리는 시간 */
    private static final long SILENCE_MILLIS = 300;

    private final Clock clock = Clock.systemDefaultZone();
    private ThreadPoolTaskScheduler scheduler;
    private GameTimers timers;

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        timers = new GameTimers(scheduler, clock);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    @Test
    @DisplayName("정해진 시각이 되면 실행한다")
    void runsAtTime() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        LocalDateTime at = now().plusNanos(200_000_000);

        timers.schedule(SESSION, at, ran::countDown);

        assertThat(ran.getCount()).isEqualTo(1);
        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(now()).isAfterOrEqualTo(at);
    }

    @Test
    @DisplayName("이미 지난 시각이면 바로 실행한다 (재시작 뒤 다시 등록할 때)")
    void runsPastTimeImmediately() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);

        timers.schedule(SESSION, now().minusMinutes(3), ran::countDown);

        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("cancelAll 하면 그 세션의 남은 타이머는 실행되지 않고, 다른 세션은 영향이 없다")
    void cancelAll() throws Exception {
        AtomicInteger cancelledRuns = new AtomicInteger();
        CountDownLatch otherRan = new CountDownLatch(1);
        LocalDateTime soon = now().plusNanos(200_000_000);
        timers.schedule(SESSION, soon, cancelledRuns::incrementAndGet);
        timers.schedule(SESSION, soon.plusSeconds(1), cancelledRuns::incrementAndGet);
        timers.schedule(OTHER_SESSION, soon, otherRan::countDown);

        timers.cancelAll(SESSION);

        assertThat(timers.pendingCount(SESSION)).isZero();
        assertThat(otherRan.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(SILENCE_MILLIS);
        assertThat(cancelledRuns).hasValue(0);
    }

    @Test
    @DisplayName("작업이 예외를 던져도 다음 타이머는 실행된다")
    void survivesFailingTask() throws Exception {
        CountDownLatch next = new CountDownLatch(1);
        LocalDateTime past = now().minusSeconds(1);

        timers.schedule(SESSION, past, () -> {
            throw new IllegalStateException("타이머 실패");
        });
        timers.schedule(SESSION, past, next::countDown);

        assertThat(next.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("끝난 타이머는 목록에서 빠지고, 남은 타이머만 센다")
    void removesFinishedTimers() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        timers.schedule(SESSION, now().minusSeconds(1), ran::countDown);
        timers.schedule(SESSION, now().plusMinutes(10), () -> { });

        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        // 실행이 끝난 뒤 목록에서 빠지기까지 잠깐 기다립니다
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (timers.pendingCount(SESSION) != 1 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
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
}
