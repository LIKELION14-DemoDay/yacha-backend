package likelion.yacha_backend.domain.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import likelion.yacha_backend.domain.user.entity.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** 실제 메일 서버 없이, 보내려던 메일(받는 사람 · 보내는 사람 · 제목 · 본문)을 붙잡아 확인함 */
@DisplayName("SMTP 메일 발송 — 메일 내용")
class SmtpMailSenderTest {

    /** 보내지 않고 모아 두는 발송기. true 면 메일 서버가 거부한 것처럼 실패함 */
    static class CapturingJavaMailSender extends JavaMailSenderImpl {

        final List<MimeMessage> sent = new ArrayList<>();
        boolean failing;

        @Override
        public void send(MimeMessage mimeMessage) {
            if (failing) {
                throw new MailSendException("SMTP 서버가 거부함");
            }
            sent.add(mimeMessage);
        }
    }

    private final CapturingJavaMailSender javaMailSender = new CapturingJavaMailSender();

    private final SmtpMailSender sender = new SmtpMailSender(javaMailSender,
            new SmtpMailProperties("smtp.example.com", 587, "user", "secret", "no-reply@example.com", "야차철학",
                    Duration.ofSeconds(5), Duration.ofSeconds(10)),
            Duration.ofMinutes(3));

    private MimeMessage onlySent() {
        assertThat(javaMailSender.sent).hasSize(1);
        return javaMailSender.sent.get(0);
    }

    @Test
    @DisplayName("인증번호 메일 — 받는 사람 · 보내는 사람(이름 포함) · 제목 · 본문의 인증번호와 유효시간")
    void resetCodeMail() throws Exception {
        sender.sendPasswordResetCode("soomin@example.com", "012345");

        MimeMessage message = onlySent();
        assertThat(message.getRecipients(RecipientType.TO)).extracting(Object::toString)
                .containsExactly("soomin@example.com");
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("no-reply@example.com");
        assertThat(from.getPersonal()).isEqualTo("야차철학");
        assertThat(message.getSubject()).isEqualTo("[야차철학] 비밀번호 재설정 인증번호");
        assertThat((String) message.getContent())
                .contains("인증번호: 012345")   // 앞자리 0 그대로
                .contains("3분 안에");
    }

    @Test
    @DisplayName("소셜 전용 계정 안내 메일 — 어느 소셜로 가입했는지 알려 주고 인증번호는 없다")
    void socialAccountMail() throws Exception {
        sender.sendPasswordResetForSocialAccount("social@example.com", Provider.KAKAO);

        MimeMessage message = onlySent();
        assertThat(message.getSubject()).isEqualTo("[야차철학] 비밀번호 찾기 안내");
        assertThat((String) message.getContent())
                .contains("카카오 로그인으로 가입한 계정")
                .doesNotContain("인증번호:");
    }

    @Test
    @DisplayName("메일 서버가 거부하면 예외를 던진다 (로그 · 처리는 PasswordResetMailer 가 함)")
    void throwsWhenServerRejects() {
        javaMailSender.failing = true;

        assertThatThrownBy(() -> sender.sendPasswordResetCode("soomin@example.com", "012345"))
                .isInstanceOf(MailSendException.class);
    }
}
