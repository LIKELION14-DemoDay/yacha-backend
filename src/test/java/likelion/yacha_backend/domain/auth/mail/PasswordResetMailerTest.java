package likelion.yacha_backend.domain.auth.mail;

import static org.assertj.core.api.Assertions.assertThatCode;

import likelion.yacha_backend.domain.user.entity.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스프링 없이 직접 만들어 확인합니다.
 * {@code @Async}를 거치면 예외가 실행기 안에서 사라져, 여기서 잡았는지 밖에서 사라졌는지 구분할 수 없기 때문입니다.
 */
@DisplayName("비밀번호 재설정 메일 발송")
class PasswordResetMailerTest {

    /** 무엇을 보내든 실패하는 발송기 */
    private final PasswordResetMailer mailer = new PasswordResetMailer(new MailSender() {
        @Override
        public void sendPasswordResetCode(String email, String code) {
            throw new IllegalStateException("메일 서버 응답 없음");
        }

        @Override
        public void sendPasswordResetForSocialAccount(String email, Provider provider) {
            throw new IllegalStateException("메일 서버 응답 없음");
        }
    });

    @Test
    @DisplayName("인증번호 발송이 실패해도 예외가 밖으로 나가지 않는다")
    void swallowsResetCodeFailure() {
        assertThatCode(() -> mailer.sendResetCode(1L, "a@example.com", "012345"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("소셜 전용 계정 안내 발송이 실패해도 예외가 밖으로 나가지 않는다")
    void swallowsSocialNoticeFailure() {
        assertThatCode(() -> mailer.sendSocialAccountNotice(1L, "a@example.com", Provider.KAKAO))
                .doesNotThrowAnyException();
    }
}
