package likelion.yacha_backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 게임 타이머(구간 전환 · 이탈 유예 · 대기 팝업 등) 전용 스케줄러.
 *
 * <p>STOMP 하트비트용 {@code messageBrokerTaskScheduler} 와 나눕니다. 게임 작업이 밀려도 연결 유지(하트비트)에
 * 영향이 없게 하기 위해서입니다. 다른 {@code TaskScheduler} 빈이 이미 있어 Spring Boot 가 기본 스케줄러를
 * 만들지 않으므로 직접 등록하고, 받을 때는 {@link #GAME_TASK_SCHEDULER} 로 지정합니다.
 *
 * <p>{@code @Scheduled} 가 아니라 코드에서 시각을 정해 등록하므로 {@code @EnableScheduling} 은 쓰지 않습니다.
 *
 * <p>타이머 작업은 짧게 끝나야 합니다. LLM 호출(판정)처럼 오래 걸리는 일은 이 스레드에서 하지 않고
 * 별도 실행기로 넘깁니다. 그래서 스레드 수는 작게 둡니다.
 */
@Configuration
public class GameSchedulerConfig {

    public static final String GAME_TASK_SCHEDULER = "gameTaskScheduler";

    /** 타이머 작업은 상태 확인 · 전송 정도라 짧습니다. 명세에서 정할 값이 아니라 상수로 둡니다. */
    private static final int POOL_SIZE = 2;

    @Bean(name = GAME_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler gameTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("game-timer-");
        // 취소한 타이머가 큐에 남아 메모리를 차지하지 않게 합니다.
        scheduler.setRemoveOnCancelPolicy(true);
        // 종료할 때 남은 타이머를 기다리지 않습니다. 재시작하면 진행 중인 게임은 어차피 ABORTED 로 정리됩니다.
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
