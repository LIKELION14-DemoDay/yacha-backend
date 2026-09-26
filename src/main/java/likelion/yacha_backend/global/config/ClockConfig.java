package likelion.yacha_backend.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각은 {@code LocalDateTime.now()} 대신 이 {@link Clock} 으로 구합니다 ({@code LocalDateTime.now(clock)}).
 * 테스트에서 {@code Clock.fixed(...)} 로 바꿔 끼워 "시작 후 정확히 240초" 같은 시점을 만들 수 있습니다.
 *
 * <p>시간대는 JVM 기본값을 따릅니다. JPA Auditing 의 {@code created_at} 도 JVM 기본 시간대로 찍히므로,
 * 둘이 어긋나지 않도록 같은 기준을 씁니다. 배포 환경의 시간대는 JVM(컨테이너) 설정으로 하나로 고정합니다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
