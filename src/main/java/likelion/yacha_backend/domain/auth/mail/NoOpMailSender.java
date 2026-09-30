package likelion.yacha_backend.domain.auth.mail;

import likelion.yacha_backend.domain.user.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 메일 인프라가 준비되기 전까지 운영에서 빈자리를 채우는 구현
 * 실제로 메일을 보내지 않음
 *
 * PasswordResetService가 MailSender를 필수로 주입받는데,
 * LoggingMailSender는 local · test 에서만 등록됨
 * 이 구현이 없으면 운영에서 빈을 찾지 못해 비밀번호 재설정뿐 아니라 애플리케이션 전체가 기동하지 못함
 *
 * LoggingMailSender와 프로필이 겹치지 않아 빈이 둘이 되는 일은 없음
 * 링크에는 재설정 토큰이 들어 있어 운영 로그에 남기지 않음
 * 메일 주소도 남기지 않음
 *
 * 실제 발송 구현(SES 등)을 붙이면 이 구현은 지움
 */
@Slf4j
@Component
@Profile("!local & !test")
public class NoOpMailSender implements MailSender {

    @Override
    public void sendPasswordReset(String email, String resetUrl) {
        log.warn("메일 발송 구현이 없어 비밀번호 재설정 메일을 보내지 못했습니다.");
    }

    @Override
    public void sendPasswordResetForSocialAccount(String email, Provider provider) {
        log.warn("메일 발송 구현이 없어 소셜 전용 계정 안내 메일을 보내지 못했습니다.");
    }
}
