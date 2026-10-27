package likelion.yacha_backend.domain.auth.mail;

import likelion.yacha_backend.domain.user.entity.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * 메일을 보내지 않는 구현
 * 운영에서 {@code mail.host}가 비어 있을 때 {@link MailSenderConfig}가 대신 등록함
 *
 * PasswordResetService가 MailSender를 필수로 주입받으므로, 이 구현이 없으면
 * 메일 설정을 아직 안 넣은 서버가 비밀번호 재설정뿐 아니라 애플리케이션 전체를 띄우지 못함
 *
 * 인증번호 · 메일 주소는 로그에 남기지 않음
 */
@Slf4j
public class NoOpMailSender implements MailSender {

    @Override
    public void sendPasswordResetCode(String email, String code) {
        log.warn("메일 발송 구현이 없어 비밀번호 재설정 인증번호 메일을 보내지 못했습니다.");
    }

    @Override
    public void sendPasswordResetForSocialAccount(String email, Provider provider) {
        log.warn("메일 발송 구현이 없어 소셜 전용 계정 안내 메일을 보내지 못했습니다.");
    }
}
