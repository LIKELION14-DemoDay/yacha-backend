package likelion.yacha_backend.domain.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Properties;
import likelion.yacha_backend.domain.auth.repository.IpRateLimit;
import likelion.yacha_backend.domain.auth.repository.PasswordResetProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;

/** 어떤 발송 구현이 등록되는지와 SMTP 연결 설정 (프로필을 바꿔 가며 보려고 Spring 없이 직접 부름) */
@DisplayName("메일 발송 구현 고르기 · SMTP 연결 설정")
class MailSenderConfigTest {

    private static final IpRateLimit IP_LIMIT = new IpRateLimit(10, Duration.ofMinutes(10));
    private static final PasswordResetProperties RESET = new PasswordResetProperties(
            Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofMinutes(3), 5, 10, 100,
            Duration.ofHours(24), IP_LIMIT, IP_LIMIT);

    private static MockEnvironment profile(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    private static SmtpMailProperties smtp(String host, int port, String username) {
        return new SmtpMailProperties(host, port, username, "secret", "no-reply@example.com", "야차철학",
                Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("mail.host 가 있으면 운영이든 local 이든 SMTP 로 보낸다 (local 에서 미리 시험해 볼 수 있음)")
    void smtpWhenConfigured() {
        MailSenderConfig config = new MailSenderConfig();

        assertThat(config.appMailSender(smtp("smtp.gmail.com", 587, "user"), RESET, profile("prod")))
                .isInstanceOf(SmtpMailSender.class);
        assertThat(config.appMailSender(smtp("smtp.gmail.com", 587, "user"), RESET, profile("local")))
                .isInstanceOf(SmtpMailSender.class);
    }

    @Test
    @DisplayName("운영에서 mail.host 가 없으면 보내지 않는 구현으로 뜬다 (서버는 막지 않고, 인증번호를 로그에 남기지도 않음)")
    void noOpInProdWhenNotConfigured() {
        MailSender sender = new MailSenderConfig().appMailSender(smtp("", 587, ""), RESET, profile("prod"));

        assertThat(sender).isInstanceOf(NoOpMailSender.class);
    }

    @Test
    @DisplayName("local · test 에서 mail.host 가 없으면 인증번호를 로그에 남기는 구현으로 뜬다")
    void loggingInLocalWhenNotConfigured() {
        MailSenderConfig config = new MailSenderConfig();

        assertThat(config.appMailSender(smtp("", 587, ""), RESET, profile("local")))
                .isInstanceOf(LoggingMailSender.class);
        assertThat(config.appMailSender(smtp("", 587, ""), RESET, profile("test")))
                .isInstanceOf(LoggingMailSender.class);
    }

    @Test
    @DisplayName("587 은 STARTTLS 를 필수로 하고, 계정이 있으면 인증하며, 타임아웃을 건다")
    void startTlsAndTimeouts() {
        JavaMailSenderImpl sender = MailSenderConfig.javaMailSender(smtp("smtp.gmail.com", 587, "user"));
        Properties mail = sender.getJavaMailProperties();

        assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(sender.getUsername()).isEqualTo("user");
        assertThat(mail).containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.smtp.timeout", "10000")
                .containsEntry("mail.smtp.writetimeout", "10000")
                .doesNotContainKey("mail.smtp.ssl.enable");
    }

    @Test
    @DisplayName("465 는 처음부터 SSL 로 연결한다")
    void sslOnSmtpsPort() {
        Properties mail = MailSenderConfig.javaMailSender(smtp("smtp.gmail.com", 465, "user")).getJavaMailProperties();

        assertThat(mail).containsEntry("mail.smtp.ssl.enable", "true")
                .doesNotContainKey("mail.smtp.starttls.enable");
    }

    @Test
    @DisplayName("계정이 없으면 인증하지 않는다")
    void noAuthWithoutCredentials() {
        JavaMailSenderImpl sender = MailSenderConfig.javaMailSender(smtp("smtp.internal", 587, ""));

        assertThat(sender.getUsername()).isNull();
        assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "false");
    }
}
