package likelion.yacha_backend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("비밀번호 변경")
class PasswordChangeApiTest {

    private static final String EMAIL = "soomin@example.com";
    private static final String OLD_PASSWORD = "password123";
    private static final String NEW_PASSWORD = "new-password-456";

    @Autowired
    private MockMvc mockMvc;

    private String accessToken;
    private Cookie refreshCookie;

    @BeforeEach
    void signup() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "nickname": "수민"}
                                """.formatted(EMAIL, OLD_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        refreshCookie = result.getResponse().getCookie("refreshToken");
    }

    private MvcResult changePassword(String token, String current, String next) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}
                                """.formatted(current, next)))
                .andReturn();
    }

    private void expectLogin(String password, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(EMAIL, password)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 바뀐다")
    void changesPassword() throws Exception {
        MvcResult result = changePassword(accessToken, OLD_PASSWORD, NEW_PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        expectLogin(NEW_PASSWORD, 200);
        expectLogin(OLD_PASSWORD, 401);   // 이전 비밀번호로는 로그인되지 않는다
    }

    @Test
    @DisplayName("바꾸면 새 토큰과 쿠키가 내려온다")
    void issuesNewTokens() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}
                                """.formatted(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    @DisplayName("바꾸면 이전 리프레시 쿠키는 무효가 된다 (다른 기기 로그아웃)")
    void invalidatesOldRefreshToken() throws Exception {
        changePassword(accessToken, OLD_PASSWORD, NEW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("응답으로 받은 새 쿠키로는 계속 재발급된다 (현재 기기 유지)")
    void keepsCurrentDeviceLoggedIn() throws Exception {
        MvcResult result = changePassword(accessToken, OLD_PASSWORD, NEW_PASSWORD);
        Cookie newCookie = result.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(newCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 401")
    void wrongCurrentPassword() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "wrong-password", "newPassword": "%s"}
                                """.formatted(NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"));

        expectLogin(OLD_PASSWORD, 200);   // 바뀌지 않았다
    }

    @Test
    @DisplayName("새 비밀번호가 현재와 같으면 400")
    void sameAsCurrent() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}
                                """.formatted(OLD_PASSWORD, OLD_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SAME_AS_CURRENT_PASSWORD"));
    }

    @Test
    @DisplayName("새 비밀번호가 8자 미만이면 400")
    void tooShortNewPassword() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "1234567"}
                                """.formatted(OLD_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("한글 비밀번호가 72바이트를 넘으면 400 (500 이 아니라)")
    void tooManyBytes() throws Exception {
        // 30자지만 90바이트입니다. 글자 수만 보면 통과하지만 BCrypt 는 72바이트까지만 받습니다.
        String korean = "가".repeat(30);

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}
                                """.formatted(OLD_PASSWORD, korean)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("72바이트 안쪽의 한글 비밀번호는 정상 동작한다")
    void koreanPasswordWithinLimit() throws Exception {
        String korean = "가".repeat(20);   // 20자 / 60바이트

        changePassword(accessToken, OLD_PASSWORD, korean);

        expectLogin(korean, 200);
    }

    @Test
    @DisplayName("비밀번호가 없는 계정(게스트)은 409")
    void guestCannotChange() throws Exception {
        MvcResult guest = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();
        String guestToken = JsonPath.read(guest.getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "whatever", "newPassword": "%s"}
                                """.formatted(NEW_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_NOT_SET"));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}
                                """.formatted(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }
}
