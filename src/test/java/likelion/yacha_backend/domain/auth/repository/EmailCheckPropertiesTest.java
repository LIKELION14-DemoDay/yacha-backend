package likelion.yacha_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("EmailCheckProperties — auth.email-check.* 설정 검증")
class EmailCheckPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(EmailCheckProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    private static final String MAX_REQUESTS = "auth.email-check.max-requests=30";

    @Test
    @DisplayName("값이 모두 있으면 기동한다")
    void valid() {
        runner.withPropertyValues(MAX_REQUESTS, "auth.email-check.window=1m")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("구간이 0 이나 음수면 기동이 실패한다 (Redis 가 키를 바로 지워 제한이 꺼지므로)")
    void nonPositiveWindow() {
        runner.withPropertyValues(MAX_REQUESTS, "auth.email-check.window=0s")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(MAX_REQUESTS, "auth.email-check.window=-1m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("밀리초로 바꾸면 0이 되는 구간(1us)도 기동이 실패하고, 1ms는 기동한다")
    void subMillisecondWindow() {
        runner.withPropertyValues(MAX_REQUESTS, "auth.email-check.window=1us")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(MAX_REQUESTS, "auth.email-check.window=1ms")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("구간이 빠지면 기동이 실패한다")
    void missingWindow() {
        runner.withPropertyValues(MAX_REQUESTS)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("허용 횟수가 빠지거나(0 으로 바인딩) 0 이하면 기동이 실패한다")
    void invalidMaxRequests() {
        runner.withPropertyValues("auth.email-check.window=1m")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("auth.email-check.max-requests=0", "auth.email-check.window=1m")
                .run(context -> assertThat(context).hasFailed());
    }
}
