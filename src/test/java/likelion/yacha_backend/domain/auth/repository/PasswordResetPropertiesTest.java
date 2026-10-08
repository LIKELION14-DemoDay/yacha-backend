package likelion.yacha_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("PasswordResetProperties — auth.password-reset.* 설정 검증")
class PasswordResetPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(PasswordResetProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    private static final String TOKEN_TTL = "auth.password-reset.token-ttl=10m";
    private static final String SEND_INTERVAL = "auth.password-reset.send-interval=1m";
    private static final String CODE_TTL = "auth.password-reset.code-ttl=3m";
    private static final String MAX_CODE_ATTEMPTS = "auth.password-reset.max-code-attempts=5";

    @Test
    @DisplayName("값이 모두 있으면 기동한다")
    void valid() {
        runner.withPropertyValues(TOKEN_TTL, SEND_INTERVAL, CODE_TTL, MAX_CODE_ATTEMPTS)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("인증번호 유효시간이 빠지면 기동이 실패한다")
    void missingCodeTtl() {
        runner.withPropertyValues(TOKEN_TTL, SEND_INTERVAL, MAX_CODE_ATTEMPTS)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("틀릴 수 있는 횟수가 빠지거나(0 으로 바인딩) 0 이하면 기동이 실패한다")
    void invalidMaxCodeAttempts() {
        runner.withPropertyValues(TOKEN_TTL, SEND_INTERVAL, CODE_TTL)
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(TOKEN_TTL, SEND_INTERVAL, CODE_TTL, "auth.password-reset.max-code-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("기간 값이 0 이나 음수면 기동이 실패한다")
    void nonPositiveDuration() {
        runner.withPropertyValues(TOKEN_TTL, SEND_INTERVAL, "auth.password-reset.code-ttl=0s", MAX_CODE_ATTEMPTS)
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("auth.password-reset.token-ttl=-1m", SEND_INTERVAL, CODE_TTL, MAX_CODE_ATTEMPTS)
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(TOKEN_TTL, "auth.password-reset.send-interval=0s", CODE_TTL, MAX_CODE_ATTEMPTS)
                .run(context -> assertThat(context).hasFailed());
    }
}
