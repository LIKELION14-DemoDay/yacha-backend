package likelion.yacha_backend.domain.auth.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import likelion.yacha_backend.domain.user.entity.Provider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP로 실제 메일을 보내는 구현 (운영)
 * {@code mail.host}가 있을 때 {@link MailSenderConfig}가 등록함
 *
 * 메일 서비스(Gmail · SES 등)는 SMTP 설정만 바꾸면 갈아끼울 수 있음
 * 발송이 실패하면 예외를 던지고, 로그 · 재시도 여부는 {@link PasswordResetMailer}가 정함
 * 예외 메시지에 받는 주소 · 인증번호를 넣지 않음 (로그에 남을 수 있어서)
 *
 * 본문은 일반 텍스트. 인증번호만 전하면 되고, HTML보다 스팸으로 분류될 가능성이 낮음
 */
public class SmtpMailSender implements MailSender {

    private static final String SUBJECT_PREFIX = "[야차철학] ";

    private final JavaMailSender javaMailSender;
    private final SmtpMailProperties properties;
    private final Duration codeTtl;

    /** @param codeTtl 인증번호 유효시간. 본문에 "N분 안에 입력" 으로 안내함 */
    public SmtpMailSender(JavaMailSender javaMailSender, SmtpMailProperties properties, Duration codeTtl) {
        this.javaMailSender = javaMailSender;
        this.properties = properties;
        this.codeTtl = codeTtl;
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        send(email, "비밀번호 재설정 인증번호", """
                야차철학 비밀번호 재설정 인증번호입니다.

                인증번호: %s

                %s 안에 화면에 입력해 주세요.
                본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다. 비밀번호는 바뀌지 않습니다.
                """.formatted(code, readable(codeTtl)));
    }

    @Override
    public void sendPasswordResetForSocialAccount(String email, Provider provider) {
        send(email, "비밀번호 찾기 안내", """
                야차철학 비밀번호 찾기를 요청하셨습니다.

                이 이메일은 %s 로그인으로 가입한 계정이라 비밀번호가 없습니다.
                로그인 화면에서 '%s로 시작하기'를 이용해 주세요.

                본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.
                """.formatted(providerName(provider), providerName(provider)));
    }

    private void send(String to, String subject, String text) {
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(to);
            helper.setSubject(SUBJECT_PREFIX + subject);
            helper.setText(text, false);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("메일을 만들지 못했습니다.", e);
        }
        // 연결 · 인증 · 전송 실패는 MailException(런타임 예외)으로 나감
        javaMailSender.send(message);
    }

    /** 3분 · 90초처럼 읽기 쉬운 말로 */
    private static String readable(Duration duration) {
        long seconds = duration.toSeconds();
        return seconds % 60 == 0 ? (seconds / 60) + "분" : seconds + "초";
    }

    private static String providerName(Provider provider) {
        return switch (provider) {
            case KAKAO -> "카카오";
            case GOOGLE -> "구글";
            case LOCAL -> "이메일";
        };
    }
}
