package likelion.yacha_backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * 테스트 전용 1분 철학 생성 실행기. src/test에 있어 운영 jar에는 들어가지 않음
 *
 * 요청한 스레드에서 바로 실행해, 응답을 받은 뒤 만들어진 글을 바로 확인할 수 있음
 */
@Configuration
@Profile("test")
public class SyncPhilosophyExecutorConfig {

    @Bean(AsyncConfig.PHILOSOPHY_EXECUTOR)
    public TaskExecutor philosophyExecutor() {
        return new SyncTaskExecutor();
    }
}
