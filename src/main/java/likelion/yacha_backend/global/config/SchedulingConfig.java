package likelion.yacha_backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@code @Scheduled} 작업 (정해진 시각에 도는 정리 작업 등)
 *
 * 이 앱에는 TaskScheduler 빈이 여러 개(STOMP 하트비트 등)라 스프링이 {@code @Scheduled} 용으로 하나를 고르지 못함
 * 그래서 {@code @Scheduled}가 찾는 이름(taskScheduler)으로 전용 스케줄러를 둠
 * 다른 스케줄러는 모두 이름으로 주입받으므로 이 빈이 끼어들지 않음
 *
 * 테스트에서는 켜지 않음. 테스트 도중 정리 작업이 돌아 데이터를 지우면 결과가 들쭉날쭉해짐
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 하루 한 번 도는 게스트 정리와 1분마다 도는 대기 방 정리뿐이고 둘 다 금방 끝나서 하나면 충분함
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}
