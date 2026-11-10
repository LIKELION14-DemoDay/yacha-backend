package likelion.yacha_backend.domain.heart.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("HeartProperties — heart.* 설정 검증")
class HeartPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(HeartProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("heart.max=3", "heart.recharge-interval=15m");

    @Test
    @DisplayName("값이 모두 있으면 기동하고 Duration 으로 읽는다")
    void valid() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            HeartProperties properties = context.getBean(HeartProperties.class);
            assertThat(properties.max()).isEqualTo(3);
            assertThat(properties.rechargeInterval()).hasMinutes(15);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("최대 하트 수가 0 이하면 기동이 실패한다")
    void nonPositiveMax(String max) {
        runner.withPropertyValues("heart.max=" + max).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1m"})
    @DisplayName("충전 간격이 0 이하면 기동이 실패한다")
    void nonPositiveInterval(String interval) {
        runner.withPropertyValues("heart.recharge-interval=" + interval)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("충전 간격이 빠지면 기동이 실패한다")
    void missingInterval() {
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withPropertyValues("heart.max=3")
                .run(context -> assertThat(context).hasFailed());
    }
}
