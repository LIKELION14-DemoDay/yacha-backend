package likelion.yacha_backend.domain.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("SmtpMailProperties — mail.* 설정 검증")
class SmtpMailPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(SmtpMailProperties.class)
    static class Config {
    }

    /** host 를 넣은 올바른 값. 테스트마다 하나를 빼거나 바꿈 */
    private static Map<String, String> configuredValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("host", "smtp.example.com");
        values.put("port", "587");
        values.put("username", "user");
        values.put("password", "secret");
        values.put("from", "no-reply@example.com");
        values.put("from-name", "야차철학");
        values.put("connection-timeout", "5s");
        values.put("timeout", "10s");
        return values;
    }

    private static ApplicationContextRunner runnerWith(Map<String, String> values) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues(values.entrySet().stream()
                        .map(entry -> "mail." + entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new));
    }

    @Test
    @DisplayName("host 와 보내는 주소가 있으면 기동하고, 실제로 보내는 설정이다")
    void configured() {
        runnerWith(configuredValues()).run(context -> {
            assertThat(context).hasNotFailed();
            SmtpMailProperties properties = context.getBean(SmtpMailProperties.class);
            assertThat(properties.isConfigured()).isTrue();
            assertThat(properties.hasCredentials()).isTrue();
        });
    }

    @Test
    @DisplayName("host 가 비어 있으면 보내는 주소가 없어도 기동하고, 보내지 않는 설정이다")
    void notConfigured() {
        Map<String, String> values = configuredValues();
        values.put("host", "");
        values.put("from", "");

        runnerWith(values).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SmtpMailProperties.class).isConfigured()).isFalse();
        });
    }

    @Test
    @DisplayName("host 가 있는데 보내는 주소가 없으면 기동이 실패한다")
    void hostWithoutFrom() {
        Map<String, String> values = configuredValues();
        values.put("from", "");

        runnerWith(values).run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("host가 있는데 아이디 · 비밀번호 중 하나만 있으면 기동이 실패한다")
    void halfCredentials() {
        for (String missing : new String[]{"password", "username"}) {
            Map<String, String> values = configuredValues();
            values.put(missing, "");
            runnerWith(values).run(context -> assertThat(context).as("%s 없음", missing).hasFailed());
        }
    }

    @Test
    @DisplayName("host가 비어 있으면 아이디만 있어도 기동한다 (보내지 않는 설정이라)")
    void halfCredentialsWithoutHost() {
        Map<String, String> values = configuredValues();
        values.put("host", "");
        values.put("password", "");

        runnerWith(values).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("계정이 비어 있으면 인증 없이 보내는 설정이다")
    void withoutCredentials() {
        Map<String, String> values = configuredValues();
        values.put("username", "");
        values.put("password", "");

        runnerWith(values).run(context ->
                assertThat(context.getBean(SmtpMailProperties.class).hasCredentials()).isFalse());
    }

    @Test
    @DisplayName("타임아웃이 1ms 미만이거나 포트가 0 이하면 기동이 실패한다")
    void invalidTimeoutsOrPort() {
        for (String[] invalid : new String[][]{
                {"connection-timeout", "0s"}, {"timeout", "1us"}, {"port", "0"}}) {
            Map<String, String> values = configuredValues();
            values.put(invalid[0], invalid[1]);
            runnerWith(values).run(context ->
                    assertThat(context).as("%s=%s", invalid[0], invalid[1]).hasFailed());
        }
    }
}
