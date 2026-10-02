package likelion.yacha_backend.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
@DisplayName("내 정보 조회 · 닉네임 변경")
class UserMeApiTest {

    @Autowired
    private MockMvc mockMvc;

    private String guestAccessToken;

    @BeforeEach
    void createGuest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();
        guestAccessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String bearer() {
        return "Bearer " + guestAccessToken;
    }

    /** 닉네임 변경은 회원 전용이라 회원으로 가입해 토큰을 받습니다. */
    private String memberBearer() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "me@example.com", "password": "password123", "nickname": "수민"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    @DisplayName("게스트도 조회할 수 있고, 이메일은 null 이며 전적은 0")
    void guestCanReadOwnInfo() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.nickname").isNotEmpty())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.isGuest").value(true))
                // 프론트가 "비밀번호 변경" 메뉴 노출을 판단하는 값
                .andExpect(jsonPath("$.data.provider").value("LOCAL"))
                .andExpect(jsonPath("$.data.stats.total").value(0))
                .andExpect(jsonPath("$.data.stats.wins").value(0));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("닉네임을 바꾸면 응답과 이후 조회에 모두 반영됨")
    void changeNickname() throws Exception {
        String member = memberBearer();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "소크라테스"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("소크라테스"));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, member))
                .andExpect(jsonPath("$.data.nickname").value("소크라테스"));
    }

    @Test
    @DisplayName("게스트는 닉네임을 바꿀 수 없음 (403 GUEST_NOT_ALLOWED)")
    void guestCannotChangeNickname() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "소크라테스"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GUEST_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("빈 닉네임이면 400")
    void rejectsBlankNickname() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, memberBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
