package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("WaitTimerProperties — game.wait.* 설정 검증")
class WaitTimerPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(WaitTimerProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    @DisplayName("간격 · 상한이 있으면 기동하고 Duration 으로 읽는다")
    void valid() {
        runner.withPropertyValues("game.wait.prompt-interval=30s", "game.wait.limit=5m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    WaitTimerProperties properties = context.getBean(WaitTimerProperties.class);
                    assertThat(properties.promptInterval()).hasSeconds(30);
                    assertThat(properties.limit()).hasMinutes(5);
                });
    }

    @Test
    @DisplayName("값이 빠지면 기동이 실패한다")
    void missing() {
        runner.withPropertyValues("game.wait.prompt-interval=30s")
                .run(context -> assertThat(context).hasFailed());
    }
}
