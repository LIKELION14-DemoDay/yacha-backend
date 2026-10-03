package likelion.yacha_backend.domain.auth.mail;

import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.global.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 메일을 요청 스레드와 떼어 보냄
 *
 * 재설정 요청은 가입 여부와 관계없이 같은 응답이어야 함
 * 요청 스레드에서 직접 보내면 둘이 달라짐
 *   응답 시간 — 가입된 이메일만 메일 서버를 기다리느라 느려짐
 *   응답 코드 — 발송이 실패하면 가입된 이메일만 500
 * 그래서 메일 전용 실행기로 넘기고 바로 돌아오며, 발송 실패는 여기서 삼킴
 *
 * 비동기 · 실패 처리를 이 한 곳에 둠
 * {@link MailSender} 구현은 발송만 하고, 실패하면 예외를 던지면 됨
 *
 * 서비스에서 이 빈을 거쳐 불러야 함
 * 같은 클래스 안에서 부르면 {@code @Async}가 적용되지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailer {

    private final MailSender mailSender;

    /** 재설정 링크 메일 */
    @Async(AsyncConfig.MAIL_EXECUTOR)
    public void sendResetLink(Long userId, String email, String resetUrl) {
        try {
            mailSender.sendPasswordReset(email, resetUrl);
        } catch (RuntimeException e) {
            logFailure("비밀번호 재설정", userId, e);
        }
    }

    /** 소셜 전용 계정 안내 메일 */
    @Async(AsyncConfig.MAIL_EXECUTOR)
    public void sendSocialAccountNotice(Long userId, String email, Provider provider) {
        try {
            mailSender.sendPasswordResetForSocialAccount(email, provider);
        } catch (RuntimeException e) {
            logFailure("소셜 전용 계정 안내", userId, e);
        }
    }

    /**
     * 예외 메시지는 남기지 않고 종류만 남김
     * 발송 라이브러리의 메시지에 받는 주소나 본문(재설정 링크)이 섞여 있을 수 있음
     */
    private void logFailure(String mailType, Long userId, RuntimeException e) {
        log.warn("{} 메일 발송에 실패했습니다. userId={}, error={}",
                mailType, userId, e.getClass().getName());
    }
}
