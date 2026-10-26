package likelion.yacha_backend.domain.auth.mail;

import java.util.Properties;
import likelion.yacha_backend.domain.auth.repository.PasswordResetProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 메일 발송 구현을 고름
 *
 *   mail.host 있음              → {@link SmtpMailSender} (실제 발송. local 에서도 MAIL_* 를 넣으면 실제로 보내 미리 시험해 볼 수 있음)
 *   mail.host 없음, local · test → {@link LoggingMailSender} (인증번호를 로그에 남김)
 *   mail.host 없음, 그 밖(운영)  → {@link NoOpMailSender} (보내지 않음) + 기동 로그 경고
 *
 * 운영에서 host가 없어도 서버는 뜨게 둠. 메일 설정을 아직 안 넣은 서버에서 로그인 · 게임까지 막히면 안 되기 때문
 * 대신 비밀번호 찾기가 동작하지 않으므로 경고를 남겨 배포 뒤 로그로 알 수 있게 함
 * 운영에서 로그 구현으로 떨어지지 않게, 로그 구현은 local · test 일 때만 고름 (인증번호가 운영 로그에 남으면 안 됨)
 *
 * Spring 기본 메일 설정(spring.mail.*)을 쓰지 않는 이유
 *   빈 문자열이어도 "설정됨"으로 보고 발송기를 만들어서, 값을 안 넣은 서버가 빈 주소로 보내려다 실패함
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SmtpMailProperties.class)
public class MailSenderConfig {

    /** 465는 처음부터 SSL, 그 밖(587 등)은 STARTTLS 로 암호화함 */
    private static final int SMTPS_PORT = 465;

    @Bean
    public MailSender appMailSender(SmtpMailProperties properties, PasswordResetProperties passwordResetProperties,
                                    Environment environment) {
        if (!properties.isConfigured()) {
            if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
                return new LoggingMailSender();
            }
            log.warn("mail.host 가 없어 메일을 보내지 않습니다. 비밀번호 찾기 인증번호가 발송되지 않으니 MAIL_* 환경변수를 확인하세요.");
            return new NoOpMailSender();
        }
        log.info("SMTP 로 메일을 보냅니다. host={}, port={}", properties.host(), properties.port());
        return new SmtpMailSender(javaMailSender(properties), properties, passwordResetProperties.codeTtl());
    }

    static JavaMailSenderImpl javaMailSender(SmtpMailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setDefaultEncoding("UTF-8");
        if (properties.hasCredentials()) {
            sender.setUsername(properties.username());
            sender.setPassword(properties.password());
        }

        Properties mail = sender.getJavaMailProperties();
        mail.put("mail.smtp.auth", String.valueOf(properties.hasCredentials()));
        if (properties.port() == SMTPS_PORT) {
            mail.put("mail.smtp.ssl.enable", "true");
        } else {
            // 암호화 없이 보내지 않게 STARTTLS 를 필수로 둠 (계정 · 인증번호가 평문으로 나가지 않게)
            mail.put("mail.smtp.starttls.enable", "true");
            mail.put("mail.smtp.starttls.required", "true");
        }
        String connectionTimeout = String.valueOf(properties.connectionTimeout().toMillis());
        String timeout = String.valueOf(properties.timeout().toMillis());
        mail.put("mail.smtp.connectiontimeout", connectionTimeout);
        mail.put("mail.smtp.timeout", timeout);
        mail.put("mail.smtp.writetimeout", timeout);
        return sender;
    }
}
