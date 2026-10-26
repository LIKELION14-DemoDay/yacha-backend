package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("InProgressSweepProperties — game.in-progress.* 설정 검증")
class InProgressSweepPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(InProgressSweepProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    @DisplayName("정리 간격이 있으면 기동하고 Duration 으로 읽는다")
    void valid() {
        runner.withPropertyValues("game.in-progress.sweep-interval=1m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(InProgressSweepProperties.class).sweepInterval()).hasMinutes(1);
                });
    }

    @Test
    @DisplayName("값이 빠지면 기동이 실패한다")
    void missing() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1m"})
    @DisplayName("정리 간격이 0 이하면 기동이 실패한다")
    void nonPositiveInterval(String interval) {
        runner.withPropertyValues("game.in-progress.sweep-interval=" + interval)
                .run(context -> assertThat(context).hasFailed());
    }
}
