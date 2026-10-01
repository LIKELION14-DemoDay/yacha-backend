package likelion.yacha_backend.global.config;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@code @Async} 작업을 돌릴 실행기
 *
 * 메일 발송은 외부 호출이라 요청 스레드에서 기다리면
 * 가입된 이메일만 응답이 느려져 가입 여부가 드러남
 * 그래서 요청 스레드는 작업만 넘기고 바로 응답함
 *
 * 기본 실행기를 쓰지 않고 메일 전용 풀을 둠
 * 메일 서버가 느려져도 쌓이는 작업 수가 정해져 있어 다른 기능에 번지지 않음
 *
 * 테스트에서는 같은 이름의 동기 실행기로 바꿔 끼움 (src/test 의 SyncMailExecutorConfig)
 * 발송 결과를 요청 직후 바로 확인할 수 있어야 하기 때문
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    @Bean(MAIL_EXECUTOR)
    @Profile("!test")
    public TaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);

        // 큐가 가득 차면 기본 정책은 요청 스레드에서 예외를 던짐
        // 그러면 가입된 계정만 500이 나가 가입 여부가 드러남
        // 요청 스레드에서 대신 보내는 정책(CallerRuns)도 응답 시간 차이가 다시 생김
        // 그래서 버리고 로그만 남김. 사용자는 1분 뒤 다시 요청하면 됨
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("메일 발송 대기열이 가득 차 요청을 버렸습니다. active={}, queued={}",
                        pool.getActiveCount(), pool.getQueue().size()));

        executor.setTaskDecorator(copyMdc());

        // 배포로 서버가 내려갈 때 대기 중인 메일을 잃지 않도록 잠시 기다림
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /**
     * 요청 스레드의 MDC(TraceIdFilter 의 traceId 등)를 작업 스레드로 옮김
     * MDC 는 스레드마다 따로라, 옮기지 않으면 발송 실패 로그를 어느 요청에서 왔는지 이어서 찾을 수 없음
     *
     * 작업이 끝나면 비움. 풀의 스레드는 재사용되므로 남겨 두면 다음 작업에 이전 요청의 값이 섞임
     */
    static TaskDecorator copyMdc() {
        return task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
