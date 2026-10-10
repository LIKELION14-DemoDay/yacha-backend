package likelion.yacha_backend.domain.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.global.config.AsyncConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 다른 테스트는 동기 실행기(SyncMailExecutorConfig)로 돌아서, 실제로 따로 떨어져 보내는지는 여기서만 확인합니다.
 * 운영과 같은 실행기를 쓰도록 test 가 아닌 프로필로 띄우고, 필요한 빈만 올립니다.
 */
@SpringJUnitConfig(PasswordResetMailerAsyncTest.Config.class)
@ActiveProfiles("async-test")
@DisplayName("비밀번호 재설정 메일 비동기 발송")
class PasswordResetMailerAsyncTest {

    @Configuration
    @Import({AsyncConfig.class, PasswordResetMailer.class})
    static class Config {

        @Bean
        ThreadRecordingMailSender mailSender() {
            return new ThreadRecordingMailSender();
        }
    }

    /** 발송이 어느 스레드에서 실행됐는지 기록합니다. */
    static class ThreadRecordingMailSender implements MailSender {

        final CompletableFuture<String> thread = new CompletableFuture<>();

        @Override
        public void sendPasswordResetCode(String email, String code) {
            thread.complete(Thread.currentThread().getName());
        }

        @Override
        public void sendPasswordResetForSocialAccount(String email, Provider provider) {
            thread.complete(Thread.currentThread().getName());
        }
    }

    @Autowired
    private PasswordResetMailer mailer;

    @Autowired
    private ThreadRecordingMailSender mailSender;

    @Test
    @DisplayName("요청 스레드가 아니라 메일 전용 스레드에서 보낸다")
    void sendsOnMailThread() throws Exception {
        mailer.sendResetCode(1L, "a@example.com", "012345");

        assertThat(mailSender.thread.get(5, TimeUnit.SECONDS))
                .startsWith("mail-")
                .isNotEqualTo(Thread.currentThread().getName());
    }
}
