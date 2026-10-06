package likelion.yacha_backend.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import likelion.yacha_backend.domain.auth.repository.InMemoryEmailCheckLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "auth.email-check.max-requests=3")
@AutoConfigureMockMvc
@Transactional
@DisplayName("회원가입 이메일 중복 확인")
class EmailAvailabilityApiTest {

    private static final String CLIENT_IP = "203.0.113.7";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryEmailCheckLimiter limiter;

    @BeforeEach
    void clearLimiter() {
        limiter.clear();
    }

    private MockHttpServletRequestBuilder check(String email, String clientIp) {
        return get("/api/v1/auth/email/availability")
                .param("email", email)
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                });
    }

    private void signup(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123", "nickname": "수민"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 로그인 없이 available true")
    void availableWithoutLogin() throws Exception {
        mockMvc.perform(check("new@example.com", CLIENT_IP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 에러가 아니라 200 + available false")
    void takenEmail() throws Exception {
        signup("taken@example.com");

        mockMvc.perform(check("taken@example.com", CLIENT_IP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("대소문자만 다른 이메일도 이미 가입된 것으로 본다")
    void ignoresCase() throws Exception {
        signup("taken@example.com");

        mockMvc.perform(check("Taken@Example.COM", CLIENT_IP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400")
    void invalidFormat() throws Exception {
        mockMvc.perform(check("not-an-email", CLIENT_IP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("email 이 없으면 400")
    void missingEmail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/email/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("같은 IP에서 정해진 횟수를 넘으면 429 TOO_MANY_REQUESTS")
    void tooManyRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(check("user" + i + "@example.com", CLIENT_IP))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(check("user4@example.com", CLIENT_IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("다른 IP는 따로 세서 막히지 않는다")
    void otherIpNotBlocked() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(check("user" + i + "@example.com", CLIENT_IP));
        }

        mockMvc.perform(check("user4@example.com", "198.51.100.2"))
                .andExpect(status().isOk());
    }
}
