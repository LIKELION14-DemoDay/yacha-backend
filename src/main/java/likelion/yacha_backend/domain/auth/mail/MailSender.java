package likelion.yacha_backend.domain.auth.mail;

import likelion.yacha_backend.domain.user.entity.Provider;

/**
 * 메일 발송
 *
 * 아직 메일 인프라가 없어서 인터페이스로 두고, 지금은 링크를 로그에 남기는 구현만 사용
 * 인프라가 준비되면 구현체만 갈아끼우면 서비스 코드는 그대로임
 *
 * 발송은 메일 서버 응답을 기다리는 외부 호출임
 * 트랜잭션 안에서 부르지 안됨
 * 응답이 늦으면 DB 커넥션을 붙잡고 있게 됨
 *
 * 서비스는 이 인터페이스를 직접 부르지 않고 {@link PasswordResetMailer}를 거침
 * 비동기 발송 · 실패 처리는 거기서 하므로, 구현은 발송만 하고 실패하면 예외를 던지면 됨
 */
public interface MailSender {

    /** 비밀번호를 재설정할 수 있는 링크를 보냄 */
    void sendPasswordReset(String email, String resetUrl);

    /**
     * 소셜로만 가입한 계정에 보냄
     * 비밀번호가 없어 재설정할 대상이 없으므로 링크 대신 "카카오로 로그인하세요" 같은 안내를 보냄
     *
     * 이 경우에도 메일은 보냄
     */
    void sendPasswordResetForSocialAccount(String email, Provider provider);
}
