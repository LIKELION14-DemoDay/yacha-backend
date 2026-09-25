package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional   // 각 테스트가 끝나면 롤백되어 서로 영향을 주지 않음
@DisplayName("회원가입 · 로그인")
class SignupLoginApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private static final String SIGNUP_BODY = """
            {"email": "Soomin@Example.com", "password": "password123", "nickname": "수민"}
            """;

    private void signup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isOk());
    }

    private String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    // 회원가입

    @Test
    @DisplayName("가입하면 바로 토큰과 쿠키가 내려오고, 게스트가 아니다")
    void signupIssuesTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.nickname").value("수민"))
                .andExpect(jsonPath("$.data.isGuest").value(false))
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    @DisplayName("이메일은 소문자로 저장되고 비밀번호는 평문으로 저장되지 않는다")
    void signupNormalizesEmailAndHashesPassword() throws Exception {
        signup();

        User saved = userRepository.findByEmail("soomin@example.com").orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("soomin@example.com");
        assertThat(saved.getPassword())
                .isNotEqualTo("password123")
                .startsWith("$2");
        assertThat(saved.isGuest()).isFalse();
    }

    @Test
    @DisplayName("같은 이메일로 다시 가입하면 409")
    void signupRejectsDuplicateEmail() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("대소문자만 다른 이메일도 중복으로 본다")
    void signupRejectsDuplicateIgnoringCase() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "SOOMIN@example.com", "password": "password123", "nickname": "수민2"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400")
    void signupRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "short@example.com", "password": "1234567", "nickname": "수민"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.password").isNotEmpty());   // 어느 필드가 틀렸는지 알려줌
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400")
    void signupRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "password123", "nickname": "수민"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // 로그인

    @Test
    @DisplayName("가입한 계정으로 로그인하면 토큰이 내려온다")
    void loginSucceeds() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("soomin@example.com", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    @DisplayName("이메일 대소문자가 달라도 로그인된다")
    void loginIgnoresEmailCase() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("SOOMIN@EXAMPLE.COM", "password123")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 LOGIN_FAILED")
    void loginRejectsWrongPassword() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("soomin@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("없는 이메일도 비밀번호 오류와 똑같이 401 LOGIN_FAILED")
    void loginHidesWhetherEmailExists() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody@example.com", "password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("/auth/token 도 /auth/login 과 같게 동작한다")
    void tokenEndpointBehavesLikeLogin() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("soomin@example.com", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"));
    }
}
