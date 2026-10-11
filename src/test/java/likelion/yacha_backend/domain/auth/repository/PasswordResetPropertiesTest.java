package likelion.yacha_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
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

    private static final String PREFIX = "auth.password-reset.";

    /** 모두 올바른 값. 테스트마다 여기서 하나를 빼거나 바꿔서 그 값만 확인함 */
    private static Map<String, String> validValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("token-ttl", "10m");
        values.put("send-interval", "1m");
        values.put("code-ttl", "3m");
        values.put("max-code-attempts", "5");
        values.put("failure-limit", "10");
        values.put("failure-window", "24h");
        values.put("request-ip-limit.max-requests", "10");
        values.put("request-ip-limit.window", "10m");
        values.put("verify-ip-limit.max-requests", "30");
        values.put("verify-ip-limit.window", "10m");
        return values;
    }

    private static ApplicationContextRunner runnerWith(Map<String, String> values) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues(values.entrySet().stream()
                        .map(entry -> PREFIX + entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new));
    }

    private static void expectStartupFailsWithout(String key) {
        Map<String, String> values = validValues();
        values.remove(key);
        runnerWith(values).run(context -> assertThat(context).as("%s 없음", key).hasFailed());
    }

    private static void expectStartupFailsWith(String key, String value) {
        Map<String, String> values = validValues();
        values.put(key, value);
        runnerWith(values).run(context -> assertThat(context).as("%s=%s", key, value).hasFailed());
    }

    @Test
    @DisplayName("값이 모두 있으면 기동한다")
    void valid() {
        runnerWith(validValues()).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("인증번호 유효시간이 빠지면 기동이 실패한다")
    void missingCodeTtl() {
        expectStartupFailsWithout("code-ttl");
    }

    @Test
    @DisplayName("틀릴 수 있는 횟수가 빠지거나(0으로 바인딩) 0 이하면 기동이 실패한다")
    void invalidMaxCodeAttempts() {
        expectStartupFailsWithout("max-code-attempts");
        expectStartupFailsWith("max-code-attempts", "0");
    }

    @Test
    @DisplayName("기간 값이 0이나 음수면 기동이 실패한다")
    void nonPositiveDuration() {
        expectStartupFailsWith("code-ttl", "0s");
        expectStartupFailsWith("token-ttl", "-1m");
        expectStartupFailsWith("send-interval", "0s");
    }

    @Test
    @DisplayName("하루 실패 한도가 0 이하이거나 기간이 0 이하면 기동이 실패한다")
    void invalidFailureSettings() {
        expectStartupFailsWith("failure-limit", "0");
        expectStartupFailsWith("failure-window", "0s");
    }

    @Test
    @DisplayName("IP 제한의 횟수가 빠지거나 0 이하이고, 구간이 빠지거나 1ms 미만이면 기동이 실패한다")
    void invalidIpLimits() {
        expectStartupFailsWithout("request-ip-limit.max-requests");
        expectStartupFailsWith("request-ip-limit.max-requests", "0");
        expectStartupFailsWithout("request-ip-limit.window");
        expectStartupFailsWith("request-ip-limit.window", "1us");
        expectStartupFailsWith("verify-ip-limit.max-requests", "0");
        expectStartupFailsWith("verify-ip-limit.window", "0s");
    }
}
