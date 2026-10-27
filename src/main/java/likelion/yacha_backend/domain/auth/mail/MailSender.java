package likelion.yacha_backend.domain.auth.mail;

import likelion.yacha_backend.domain.user.entity.Provider;

/**
 * 메일 발송
 *
 * 운영은 SMTP로 실제 발송하고({@link SmtpMailSender}, 설정이 없으면 {@link NoOpMailSender}),
 * local · test 는 인증번호를 로그에 남김({@link LoggingMailSender}). 고르는 곳은 {@link MailSenderConfig}
 *
 * 발송은 메일 서버 응답을 기다리는 외부 호출임
 * 트랜잭션 안에서 부르지 안됨
 * 응답이 늦으면 DB 커넥션을 붙잡고 있게 됨
 *
 * 서비스는 이 인터페이스를 직접 부르지 않고 {@link PasswordResetMailer}를 거침
 * 비동기 발송 · 실패 처리는 거기서 하므로, 구현은 발송만 하고 실패하면 예외를 던지면 됨
 */
public interface MailSender {

    /** 비밀번호 재설정 인증번호(6자리)를 보냄. 3분 동안 유효하다고 안내 */
    void sendPasswordResetCode(String email, String code);

    /**
     * 소셜로만 가입한 계정에 보냄
     * 비밀번호가 없어 재설정할 대상이 없으므로 인증번호 대신 "카카오로 로그인하세요"같은 안내를 보냄
     *
     * 이 경우에도 메일은 보냄
     */
    void sendPasswordResetForSocialAccount(String email, Provider provider);
}
