package likelion.yacha_backend.domain.auth.mail;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml의 {@code mail.*} — 운영 메일 발송(SMTP)
 *
 * host가 비어 있으면 메일을 보내지 않음 (발송 구현이 없는 것과 같음, {@link MailSenderConfig})
 * host가 있으면 보내는 주소도 있어야 함. 없으면 서버가 뜰 때 실패함 (배포하고 나서야 발송이 실패하는 것을 막음)
 *
 * @param host              SMTP 서버. 예: SES {@code email-smtp.ap-northeast-2.amazonaws.com}, Gmail {@code smtp.gmail.com}
 * @param port              587 (STARTTLS)
 * @param username          SMTP 계정. 인증이 없는 서버면 비워 둠
 * @param password          SMTP 비밀번호. 비밀 값이라 환경변수로만 받음
 * @param from              보내는 주소. SES는 인증한 도메인의 주소, Gmail은 그 Gmail 주소여야 함
 * @param fromName          보내는 사람 이름 (메일함에 보이는 이름)
 * @param connectionTimeout 메일 서버 연결 대기 상한
 * @param timeout           메일 서버 응답 대기 상한. 메일 서버가 멈춰도 발송 스레드가 오래 묶이지 않게 함
 */
@Validated
@ConfigurationProperties(prefix = "mail")
public record SmtpMailProperties(
        String host,
        @Positive int port,
        String username,
        String password,
        String from,
        String fromName,
        @NotNull Duration connectionTimeout,
        @NotNull Duration timeout
) {

    /** host가 있을 때만 실제로 보냄 */
    public boolean isConfigured() {
        return hasText(host);
    }

    /** 계정이 있으면 SMTP 인증을 함 */
    public boolean hasCredentials() {
        return hasText(username);
    }

    @AssertTrue(message = "mail.host 가 있으면 mail.from(보내는 주소)도 있어야 합니다.")
    public boolean isFromSetWhenConfigured() {
        return !isConfigured() || hasText(from);
    }

    /** 타임아웃은 밀리초로 넘기므로 1ms 이상이어야 함. 0이면 무한 대기가 됨 (null은 위의 @NotNull이 검사) */
    @AssertTrue(message = "mail.connection-timeout · mail.timeout 은 1ms 이상이어야 합니다.")
    public boolean isTimeoutsAtLeastOneMillisecond() {
        return (connectionTimeout == null || connectionTimeout.toMillis() >= 1)
                && (timeout == null || timeout.toMillis() >= 1);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
