package likelion.yacha_backend.domain.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("재설정 링크 만들기")
class MailPropertiesTest {

    @Test
    @DisplayName("기본 형태")
    void buildsUrl() {
        MailProperties properties = new MailProperties("https://yacha.com", "/reset-password");

        assertThat(properties.passwordResetUrl("abc123"))
                .isEqualTo("https://yacha.com/reset-password?token=abc123");
    }

    @Test
    @DisplayName("설정값 끝에 슬래시가 있어도 // 가 되지 않는다")
    void trailingSlash() {
        MailProperties properties = new MailProperties("https://yacha.com/", "/reset-password");

        assertThat(properties.passwordResetUrl("abc123"))
                .isEqualTo("https://yacha.com/reset-password?token=abc123");
    }

    @Test
    @DisplayName("경로 앞에 슬래시가 없어도 붙여 준다")
    void pathWithoutLeadingSlash() {
        MailProperties properties = new MailProperties("https://yacha.com", "reset-password");

        assertThat(properties.passwordResetUrl("abc123"))
                .isEqualTo("https://yacha.com/reset-password?token=abc123");
    }
}
