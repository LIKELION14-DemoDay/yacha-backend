package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("JudgeProperties — game.judge.* 설정 검증")
class JudgePropertiesTest {

    @Configuration
    @EnableConfigurationProperties(JudgeProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("game.judge.max-attempts=3", "game.judge.retry-backoff=1s",
                    "game.judge.max-rejudges=2", "game.judge.result-retention=10m");

    @Test
    @DisplayName("값이 모두 있으면 기동하고 Duration 으로 읽는다")
    void valid() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            JudgeProperties properties = context.getBean(JudgeProperties.class);
            assertThat(properties.retryBackoff()).hasSeconds(1);
            assertThat(properties.resultRetention()).hasMinutes(10);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1m"})
    @DisplayName("결과 보관 시간이 0 이하면 기동이 실패한다")
    void nonPositiveRetention(String retention) {
        runner.withPropertyValues("game.judge.result-retention=" + retention)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("재판정 횟수가 빠지면 기동이 실패하고, 0 은 재판정을 끄는 값으로 받는다")
    void maxRejudges() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("game.judge.max-attempts=3", "game.judge.retry-backoff=1s",
                        "game.judge.result-retention=10m")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("game.judge.max-rejudges=0").run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("game.judge.max-rejudges=-1").run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("재시도 대기는 0 이면 기동하고, 음수면 실패한다")
    void retryBackoff() {
        runner.withPropertyValues("game.judge.retry-backoff=0s").run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("game.judge.retry-backoff=-1s").run(context -> assertThat(context).hasFailed());
    }
}
