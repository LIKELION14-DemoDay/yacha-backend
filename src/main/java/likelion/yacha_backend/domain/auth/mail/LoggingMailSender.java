package likelion.yacha_backend.domain.auth.mail;

import likelion.yacha_backend.domain.user.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 메일 인프라가 준비되기 전까지 쓰는 구현. <b>실제로 메일을 보내지 않고 로그에 남깁니다.</b>
 *
 * <p>개발 중에는 로그에 찍힌 링크를 복사해 재설정 흐름을 끝까지 테스트할 수 있습니다.
 *
 * <p>{@code @ConditionalOnMissingBean} 이라, 나중에 실제 발송 구현({@code SesMailSender} 등)을
 * 빈으로 등록하면 이 클래스는 자동으로 빠집니다. 그때 서비스 코드는 고칠 게 없습니다.
 *
 * <p>⚠️ 배포에 이 구현이 올라가면 사용자는 메일을 받지 못합니다. 실제 발송 구현을 붙일 때
 * 기동 로그에서 어떤 구현이 등록됐는지 확인하세요.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(MailSender.class)
public class LoggingMailSender implements MailSender {

    @Override
    public void sendPasswordReset(String email, String resetUrl) {
        // 개발 편의를 위해 링크를 그대로 찍습니다. 운영 발송 구현에서는 링크를 로그에 남기면 안 됩니다.
        // 로그를 볼 수 있는 사람이 남의 비밀번호를 바꿀 수 있게 됩니다.
        log.info("[메일 발송 생략] 비밀번호 재설정 링크: to={}, url={}", email, resetUrl);
    }

    @Override
    public void sendPasswordResetForSocialAccount(String email, Provider provider) {
        log.info("[메일 발송 생략] 소셜 전용 계정 안내: to={}, provider={}", email, provider);
    }
}
