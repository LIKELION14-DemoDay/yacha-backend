package likelion.yacha_backend.domain.auth.mail;

import likelion.yacha_backend.domain.user.entity.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * 실제로 메일을 보내지 않고 로그에 남기는 구현
 * local · test 에서 {@code mail.host}가 비어 있을 때 {@link MailSenderConfig}가 등록함
 *
 * 개발 중에는 로그에 찍힌 인증번호를 입력해 재설정 흐름을 끝까지 테스트할 수 있음
 * 운영 로그에 인증번호가 남으면 로그를 볼 수 있는 사람이 남의 비밀번호를 바꿀 수 있어 운영에서는 쓰지 않음
 */
@Slf4j
public class LoggingMailSender implements MailSender {

    @Override
    public void sendPasswordResetCode(String email, String code) {
        // 개발 편의를 위해 인증번호를 그대로 찍음
        // 운영 발송 구현에서는 인증번호를 로그에 남기면 안 됨
        // 로그를 볼 수 있는 사람이 남의 비밀번호를 바꿀 수 있게 됨
        log.info("[메일 발송 생략] 비밀번호 재설정 인증번호: to={}, code={}", email, code);
    }

    @Override
    public void sendPasswordResetForSocialAccount(String email, Provider provider) {
        log.info("[메일 발송 생략] 소셜 전용 계정 안내: to={}, provider={}", email, provider);
    }
}
