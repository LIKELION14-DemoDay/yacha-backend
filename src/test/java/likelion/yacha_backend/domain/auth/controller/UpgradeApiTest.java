package likelion.yacha_backend.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@DisplayName("게스트 → 회원 승격")
class UpgradeApiTest {

    @Autowired
    private MockMvc mockMvc;

    private String guestAccessToken;
    private Cookie guestRefreshCookie;
    private Integer guestUserId;

    private static final String UPGRADE_BODY = """
            {"email": "Upgraded@Example.com", "password": "password123", "nickname": "멋사"}
            """;

    @BeforeEach
    void createGuest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();
        String body = result.getResponse().getContentAsString();

        guestAccessToken = JsonPath.read(body, "$.data.accessToken");
        guestUserId = JsonPath.read(body, "$.data.userId");
        guestRefreshCookie = result.getResponse().getCookie("refreshToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("승격하면 같은 계정이 회원이 됨 (userId 유지 = 기록 이어짐)")
    void upgradeKeepsSameAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andExpect(status().isOk())
                // 새 계정이 아니라 같은 행이어야 합니다. id 가 바뀌면 토론 기록이 끊깁니다.
                .andExpect(jsonPath("$.data.userId").value(guestUserId))
                .andExpect(jsonPath("$.data.isGuest").value(false))
                .andExpect(jsonPath("$.data.nickname").value("수민"));
    }

    @Test
    @DisplayName("승격 후 이메일이 소문자로 저장되어 그 계정으로 로그인")
    void canLoginAfterUpgrade() throws Exception {
        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "upgraded@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(guestUserId));
    }

    @Test
    @DisplayName("승격하면 토큰이 새로 발급되어 예전 리프레시 쿠키는 쓸 수 없음")
    void oldRefreshTokenIsInvalidatedAfterUpgrade() throws Exception {
        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(guestRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("승격 후 내 정보에 이메일이 보이고 게스트가 아님")
    void myInfoReflectsUpgrade() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andReturn();

        String newAccessToken = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.accessToken");

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
                .andExpect(jsonPath("$.data.email").value("upgraded@example.com"))
                .andExpect(jsonPath("$.data.isGuest").value(false));
    }

    @Test
    @DisplayName("이미 회원이면 409 ALREADY_MEMBER")
    void rejectsAlreadyMember() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andReturn();

        String newAccessToken = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.accessToken");

        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "another@example.com", "password": "password123", "nickname": "멋사"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"));
    }

    @Test
    @DisplayName("다른 사람이 쓰는 이메일이면 409 EMAIL_ALREADY_EXISTS")
    void rejectsDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "taken@example.com", "password": "password123", "nickname": "먼저"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(guestAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "taken@example.com", "password": "password123", "nickname": "멋사"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPGRADE_BODY))
                .andExpect(status().isUnauthorized());
    }
}
