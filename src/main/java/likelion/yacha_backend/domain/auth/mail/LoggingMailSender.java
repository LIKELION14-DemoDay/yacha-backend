package likelion.yacha_backend.domain.auth.mail;

import likelion.yacha_backend.domain.user.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 메일 인프라가 준비되기 전까지 쓰는 구현
 * 실제로 메일을 보내지 않고 로그에 남김
 *
 * 개발 중에는 로그에 찍힌 인증번호를 입력해 재설정 흐름을 끝까지 테스트할 수 있음
 * local · test 프로필에서만 등록됨 (그 밖의 프로필은 NoOpMailSender)
 * 운영 로그에 인증번호가 남으면 로그를 볼 수 있는 사람이 남의 비밀번호를 바꿀 수 있음
 *
 * 단, 프로필을 지정하지 않으면 기본값(local)으로 떠서 이 구현이 등록됨
 * 배포에서는 SPRING_PROFILES_ACTIVE 를 반드시 지정해야 함
 * 실제 발송 구현을 붙일 때 기동 로그에서 어떤 구현이 등록됐는지 확인해야 함
 */
@Slf4j
@Component
@Profile({"local", "test"})
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
