package likelion.yacha_backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * 테스트 전용 메일 실행기. src/test 에 있어 운영 jar 에는 들어가지 않습니다.
 *
 * {@code @Async} 는 그대로 거치지만 호출한 스레드에서 바로 실행합니다.
 * 비동기로 돌면 요청 직후 보낸 메일을 꺼낼 때 아직 기록되지 않았을 수 있어 테스트가 들쭉날쭉해집니다.
 */
@Configuration
@Profile("test")
public class SyncMailExecutorConfig {

    @Bean(AsyncConfig.MAIL_EXECUTOR)
    public TaskExecutor mailExecutor() {
        return new SyncTaskExecutor();
    }
}
