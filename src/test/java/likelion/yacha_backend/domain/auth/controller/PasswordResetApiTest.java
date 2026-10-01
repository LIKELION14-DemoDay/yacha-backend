package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import likelion.yacha_backend.domain.auth.mail.MailSender;
import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메일 발송을 기록만 하는 가짜 구현으로 바꿔 끼워, 링크에 실린 토큰을 꺼내 재설정까지 이어서 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PasswordResetApiTest.RecordingMailConfig.class)
@DisplayName("비밀번호 재설정")
class PasswordResetApiTest {

    /**
     * 재요청 제한(1분)은 인메모리 저장소에 남아 테스트 롤백으로 지워지지 않습니다.
     * 테스트마다 다른 이메일을 써서 서로 간섭하지 않게 합니다.
     */
    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger();

    private String email;
    private static final String OLD_PASSWORD = "password123";
    private static final String NEW_PASSWORD = "new-password-456";

    /** 보낸 메일을 모아 두는 가짜 발송기. 이름을 실제 빈과 다르게 두고 @Primary 로 우선합니다. */
    @TestConfiguration
    static class RecordingMailConfig {

        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }

    static class RecordingMailSender implements MailSender {

        final List<String> resetUrls = new ArrayList<>();
        final List<Provider> socialNotices = new ArrayList<>();

        /** true 면 메일 서버가 죽은 것처럼 발송마다 예외를 던집니다. 시도 횟수는 그대로 셉니다. */
        boolean failing;
        int attempts;

        @Override
        public void sendPasswordReset(String email, String resetUrl) {
            attempt();
            resetUrls.add(resetUrl);
        }

        @Override
        public void sendPasswordResetForSocialAccount(String email, Provider provider) {
            attempt();
            socialNotices.add(provider);
        }

        private void attempt() {
            attempts++;
            if (failing) {
                throw new IllegalStateException("메일 서버 응답 없음");
            }
        }

        void clear() {
            resetUrls.clear();
            socialNotices.clear();
            failing = false;
            attempts = 0;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    private Cookie refreshCookie;

    @BeforeEach
    void signup() throws Exception {
        mailSender.clear();
        email = "reset-%d@example.com".formatted(SEQ.incrementAndGet());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "nickname": "수민"}
                                """.formatted(email, OLD_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        refreshCookie = result.getResponse().getCookie("refreshToken");
    }

    private void requestReset(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    /** 비밀번호 없이 카카오로만 가입한 계정을 만들고 그 이메일을 돌려줍니다. */
    private String saveSocialUser() {
        String socialEmail = "social-%d@example.com".formatted(SEQ.incrementAndGet());
        userRepository.save(User.createSocial(Provider.KAKAO, "kakao-" + socialEmail, socialEmail, "소셜"));
        return socialEmail;
    }

    /** 메일 링크에서 토큰만 꺼냅니다. */
    private String lastToken() {
        String url = mailSender.resetUrls.get(mailSender.resetUrls.size() - 1);
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    private void reset(String token, String newPassword, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "newPassword": "%s"}
                                """.formatted(token, newPassword)))
                .andExpect(status().is(expectedStatus));
    }

    private void expectLogin(String password, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().is(expectedStatus));
    }

    // --- 메일 요청 ---------------------------------------------------

    @Test
    @DisplayName("가입된 이메일로 요청하면 재설정 링크가 발송된다")
    void sendsResetLink() throws Exception {
        requestReset(email);

        assertThat(mailSender.resetUrls).hasSize(1);
        assertThat(mailSender.resetUrls.get(0)).contains("/reset-password?token=");
    }

    @Test
    @DisplayName("대소문자가 달라도 같은 계정으로 본다")
    void ignoresEmailCase() throws Exception {
        requestReset(email.toUpperCase(java.util.Locale.ROOT));

        assertThat(mailSender.resetUrls).hasSize(1);
    }

    @Test
    @DisplayName("가입되지 않은 이메일도 200이고, 메일은 가지 않는다")
    void doesNotRevealWhetherEmailExists() throws Exception {
        requestReset("nobody@example.com");

        assertThat(mailSender.resetUrls).isEmpty();
        assertThat(mailSender.socialNotices).isEmpty();
    }

    @Test
    @DisplayName("같은 이메일로 연속 요청하면 두 번째는 메일이 가지 않는다 (1분 제한)")
    void limitsRepeatedRequests() throws Exception {
        requestReset(email);
        requestReset(email);

        // 응답은 둘 다 200이지만 메일은 한 번만 갑니다.
        assertThat(mailSender.resetUrls).hasSize(1);
    }

    @Test
    @DisplayName("소셜 전용 계정에는 링크 대신 안내 메일이 간다")
    void sendsNoticeToSocialAccount() throws Exception {
        String socialEmail = saveSocialUser();

        requestReset(socialEmail);

        assertThat(mailSender.resetUrls).isEmpty();
        assertThat(mailSender.socialNotices).containsExactly(Provider.KAKAO);
    }

    @Test
    @DisplayName("재설정 링크 발송이 실패해도 200 (가입 여부가 드러나지 않음)")
    void hidesResetLinkFailure() throws Exception {
        mailSender.failing = true;

        // requestReset 이 200 을 확인합니다.
        requestReset(email);

        assertThat(mailSender.attempts).isEqualTo(1);
    }

    @Test
    @DisplayName("소셜 전용 계정 안내 발송이 실패해도 200")
    void hidesSocialNoticeFailure() throws Exception {
        String socialEmail = saveSocialUser();
        mailSender.failing = true;

        requestReset(socialEmail);

        assertThat(mailSender.attempts).isEqualTo(1);
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400")
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("이메일이 255자를 넘으면 400")
    void rejectsTooLongEmail() throws Exception {
        // @Email은 통과하는 형식이어야 길이 제한만 확인할 수 있음 (로컬 64자·라벨 63자 이하)
        String label = "b".repeat(60);
        String longEmail = "a".repeat(10) + "@" + String.join(".", label, label, label, label) + ".com";

        mockMvc.perform(post("/api/v1/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(longEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // --- 재설정 -----------------------------------------------------

    @Test
    @DisplayName("링크의 토큰으로 비밀번호가 바뀐다")
    void resetsPassword() throws Exception {
        requestReset(email);

        reset(lastToken(), NEW_PASSWORD, 200);

        expectLogin(NEW_PASSWORD, 200);
        expectLogin(OLD_PASSWORD, 401);
    }

    @Test
    @DisplayName("같은 토큰을 두 번 쓰면 401 (1회용)")
    void tokenIsSingleUse() throws Exception {
        requestReset(email);
        String token = lastToken();
        reset(token, NEW_PASSWORD, 200);

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "newPassword": "another-password-1"}
                                """.formatted(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    @DisplayName("없는 토큰이면 401")
    void unknownToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "no-such-token", "newPassword": "%s"}
                                """.formatted(NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    @DisplayName("재설정하면 이전 리프레시 쿠키는 무효가 된다 (모든 기기 로그아웃)")
    void revokesExistingSessions() throws Exception {
        requestReset(email);
        reset(lastToken(), NEW_PASSWORD, 200);

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("새 비밀번호가 8자 미만이면 400")
    void rejectsShortPassword() throws Exception {
        requestReset(email);

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "newPassword": "1234567"}
                                """.formatted(lastToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("토큰은 매번 다르게 발급된다")
    void tokensAreUnique() throws Exception {
        requestReset(email);
        String first = lastToken();

        // 재요청 제한을 피하려고 다른 계정으로 한 번 더 확인합니다.
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "other@example.com", "password": "password123", "nickname": "다른사람"}
                                """))
                .andExpect(status().isOk());
        requestReset("other@example.com");

        assertThat(lastToken()).isNotEqualTo(first);
    }

    @Test
    @DisplayName("발급된 토큰은 URL 에 그대로 넣을 수 있는 형식이다")
    void tokenIsUrlSafe() throws Exception {
        requestReset(email);

        assertThat(lastToken()).matches("[A-Za-z0-9_-]+");
    }

}
