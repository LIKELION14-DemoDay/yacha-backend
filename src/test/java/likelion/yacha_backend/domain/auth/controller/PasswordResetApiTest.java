package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import likelion.yacha_backend.domain.auth.mail.MailSender;
import likelion.yacha_backend.domain.auth.repository.InMemoryPasswordResetStore;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메일 발송을 기록만 하는 가짜 구현으로 바꿔 끼워,
 * 메일에 실린 인증번호로 확인 → 재설정까지 이어서 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PasswordResetApiTest.RecordingMailConfig.class)
@DisplayName("비밀번호 재설정")
class PasswordResetApiTest {

    /**
     * 재요청 제한(1분)과 인증번호는 인메모리 저장소에 남아 테스트 롤백으로 지워지지 않습니다.
     * 테스트마다 다른 이메일을 써서 서로 간섭하지 않게 합니다.
     */
    private static final AtomicInteger SEQ = new AtomicInteger();

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

        final List<String> codes = new ArrayList<>();
        final List<Provider> socialNotices = new ArrayList<>();

        /** true 면 메일 서버가 죽은 것처럼 발송마다 예외를 던집니다. 시도 횟수는 그대로 셉니다. */
        boolean failing;
        int attempts;

        @Override
        public void sendPasswordResetCode(String email, String code) {
            attempt();
            codes.add(code);
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
            codes.clear();
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
    private InMemoryPasswordResetStore passwordResetStore;

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

    private ResultActions verify(String email, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "code": "%s"}
                        """.formatted(email, code)));
    }

    /** 인증번호를 확인하고 받은 재설정 토큰을 돌려줍니다. */
    private String verifyAndGetToken(String email, String code) throws Exception {
        MvcResult result = verify(email, code).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.resetToken");
    }

    /** 비밀번호 없이 카카오로만 가입한 계정을 만들고 그 이메일을 돌려줍니다. */
    private String saveSocialUser() {
        String socialEmail = "social-%d@example.com".formatted(SEQ.incrementAndGet());
        userRepository.save(User.createSocial(Provider.KAKAO, "kakao-" + socialEmail, socialEmail, "소셜"));
        return socialEmail;
    }

    private String lastCode() {
        return mailSender.codes.get(mailSender.codes.size() - 1);
    }

    /** 메일로 받은 인증번호와 다른 번호 */
    private String wrongCode() {
        return lastCode().equals("000000") ? "111111" : "000000";
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

    // --- ① 인증번호 요청 -----------------------------------------------

    @Test
    @DisplayName("가입된 이메일로 요청하면 6자리 인증번호가 발송된다")
    void sendsResetCode() throws Exception {
        requestReset(email);

        assertThat(mailSender.codes).hasSize(1);
        assertThat(lastCode()).matches("\\d{6}");
    }

    @Test
    @DisplayName("대소문자가 달라도 같은 계정으로 본다")
    void ignoresEmailCase() throws Exception {
        requestReset(email.toUpperCase(Locale.ROOT));

        assertThat(mailSender.codes).hasSize(1);
    }

    @Test
    @DisplayName("가입되지 않은 이메일도 200이고, 메일은 가지 않는다")
    void doesNotRevealWhetherEmailExists() throws Exception {
        requestReset("nobody@example.com");

        assertThat(mailSender.codes).isEmpty();
        assertThat(mailSender.socialNotices).isEmpty();
    }

    @Test
    @DisplayName("같은 이메일로 연속 요청하면 두 번째는 메일이 가지 않는다 (1분 제한)")
    void limitsRepeatedRequests() throws Exception {
        requestReset(email);
        requestReset(email);

        // 응답은 둘 다 200이지만 메일은 한 번만 갑니다.
        assertThat(mailSender.codes).hasSize(1);
    }

    @Test
    @DisplayName("소셜 전용 계정에는 인증번호 대신 안내 메일이 간다")
    void sendsNoticeToSocialAccount() throws Exception {
        String socialEmail = saveSocialUser();

        requestReset(socialEmail);

        assertThat(mailSender.codes).isEmpty();
        assertThat(mailSender.socialNotices).containsExactly(Provider.KAKAO);
    }

    @Test
    @DisplayName("인증번호 발송이 실패해도 200 (가입 여부가 드러나지 않음)")
    void hidesResetCodeFailure() throws Exception {
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

    // --- ② 인증번호 확인 -----------------------------------------------

    @Test
    @DisplayName("메일로 받은 인증번호가 맞으면 재설정 토큰을 준다")
    void verifiesCode() throws Exception {
        requestReset(email);

        verify(email, lastCode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resetToken").isNotEmpty());
    }

    @Test
    @DisplayName("인증번호가 틀리면 400 INVALID_RESET_CODE")
    void wrongCode400() throws Exception {
        requestReset(email);

        verify(email, wrongCode())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_CODE"));
    }

    @Test
    @DisplayName("5번 틀리면 맞는 번호도 400 RESET_CODE_EXPIRED")
    void expiresAfterFiveWrongCodes() throws Exception {
        requestReset(email);
        for (int i = 0; i < 5; i++) {
            verify(email, wrongCode()).andExpect(jsonPath("$.error.code").value("INVALID_RESET_CODE"));
        }

        verify(email, lastCode())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("같은 인증번호로 두 번 확인할 수 없다 (1회용)")
    void codeIsSingleUse() throws Exception {
        requestReset(email);
        verifyAndGetToken(email, lastCode());

        verify(email, lastCode())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("새로 요청하면 이전 인증번호로는 확인할 수 없다")
    void newRequestInvalidatesPreviousCode() throws Exception {
        requestReset(email);
        String first = lastCode();
        passwordResetStore.clearSendSlots();   // 1분 재요청 제한을 기다리지 않음

        requestReset(email);
        String second = lastCode();

        if (!first.equals(second)) {
            verify(email, first).andExpect(jsonPath("$.error.code").value("INVALID_RESET_CODE"));
        }
        verify(email, second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("요청한 적 없으면 400 RESET_CODE_EXPIRED")
    void notRequested() throws Exception {
        verify(email, "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("가입되지 않은 이메일도 가입된 이메일과 같은 응답을 받는다")
    void unknownEmailBehavesTheSame() throws Exception {
        // 요청 전: 둘 다 EXPIRED
        verify("nobody-verify@example.com", "123456")
                .andExpect(jsonPath("$.error.code").value("RESET_CODE_EXPIRED"));

        // 요청 후: 틀린 번호는 둘 다 INVALID_RESET_CODE (보내지 않은 인증번호도 저장해 두기 때문)
        requestReset("nobody-verify@example.com");
        requestReset(email);
        verify("nobody-verify@example.com", wrongCode())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_CODE"));
        verify(email, wrongCode())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_CODE"));
    }

    @Test
    @DisplayName("인증번호가 숫자 6자리가 아니면 시도 횟수를 쓰지 않고 400 VALIDATION_FAILED")
    void rejectsMalformedCode() throws Exception {
        requestReset(email);

        verify(email, "12345").andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        verify(email, "abcdef").andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        // 형식 오류는 횟수에 들어가지 않아 그대로 통과
        verify(email, lastCode()).andExpect(status().isOk());
    }

    // --- ③ 재설정 -----------------------------------------------------

    @Test
    @DisplayName("인증번호 확인 → 받은 토큰으로 비밀번호가 바뀐다")
    void resetsPassword() throws Exception {
        requestReset(email);
        String token = verifyAndGetToken(email, lastCode());

        reset(token, NEW_PASSWORD, 200);

        expectLogin(NEW_PASSWORD, 200);
        expectLogin(OLD_PASSWORD, 401);
    }

    @Test
    @DisplayName("같은 토큰을 두 번 쓰면 401 (1회용)")
    void tokenIsSingleUse() throws Exception {
        requestReset(email);
        String token = verifyAndGetToken(email, lastCode());
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
        reset(verifyAndGetToken(email, lastCode()), NEW_PASSWORD, 200);

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("새 비밀번호가 8자 미만이면 400")
    void rejectsShortPassword() throws Exception {
        requestReset(email);
        String token = verifyAndGetToken(email, lastCode());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "newPassword": "1234567"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
