package likelion.yacha_backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("GuestCleanupProperties — auth.guest-cleanup.* 설정 검증")
class GuestCleanupPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(GuestCleanupProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    private static final String INACTIVE_AFTER = "auth.guest-cleanup.inactive-after=14d";
    private static final String BATCH_SIZE = "auth.guest-cleanup.batch-size=500";
    private static final String CRON = "auth.guest-cleanup.cron=0 0 4 * * *";

    @Test
    @DisplayName("값이 모두 있으면 기동한다")
    void valid() {
        runner.withPropertyValues(INACTIVE_AFTER, BATCH_SIZE, CRON)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("정리 기준 기간이 빠지면 기동이 실패한다")
    void missingInactiveAfter() {
        runner.withPropertyValues(BATCH_SIZE, CRON)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("묶음 크기가 빠지거나(0 으로 바인딩) 0 이하면 기동이 실패한다")
    void invalidBatchSize() {
        runner.withPropertyValues(INACTIVE_AFTER, CRON)
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(INACTIVE_AFTER, "auth.guest-cleanup.batch-size=0", CRON)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("실행 시각이 비어 있으면 기동이 실패한다")
    void blankCron() {
        runner.withPropertyValues(INACTIVE_AFTER, BATCH_SIZE, "auth.guest-cleanup.cron=")
                .run(context -> assertThat(context).hasFailed());
    }
}
