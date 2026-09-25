package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import likelion.yacha_backend.domain.auth.repository.RefreshTokenStore;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("토큰 재발급 · 로그아웃")
class RefreshLogoutApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Cookie refreshCookie;
    private String accessToken;
    private Long userId;

    /** 게스트를 만들어 로그인된 상태를 준비 */
    @BeforeEach
    void createGuest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();

        refreshCookie = result.getResponse().getCookie("refreshToken");
        accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        userId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.userId"))
                .longValue();
    }

    // 재발급

    @Test
    @DisplayName("쿠키만으로 재발급된다 (Authorization 헤더 없이)")
    void reissueWithCookieOnly() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    @DisplayName("재발급하면 저장소의 리프레시 토큰이 새 값으로 바뀐다")
    void reissueRotatesStoredToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie))
                .andReturn();

        String newRefreshToken = result.getResponse().getCookie("refreshToken").getValue();
        assertThat(refreshTokenStore.find(userId)).contains(newRefreshToken);
    }

    @Test
    @DisplayName("쿠키가 없으면 401")
    void reissueWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("액세스 토큰을 쿠키에 넣으면 401 (토큰 종류 검사)")
    void reissueRejectsAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .cookie(new Cookie("refreshToken", accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("이미 사용된 리프레시 토큰이면 401이고, 저장된 토큰까지 폐기된다 (재사용 탐지)")
    void reissueDetectsReuse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        assertThat(refreshTokenStore.find(userId)).isEmpty();
    }

    @Test
    @DisplayName("위조된 토큰이면 401")
    void reissueRejectsTamperedToken() throws Exception {
        String tampered = refreshCookie.getValue() + "x";

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .cookie(new Cookie("refreshToken", tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    // 로그아웃

    @Test
    @DisplayName("로그아웃하면 저장된 토큰이 지워지고 쿠키도 만료된다")
    void logout() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        assertThat(refreshTokenStore.find(userId)).isEmpty();
        assertThat(result.getResponse().getCookie("refreshToken").getMaxAge()).isZero();
    }

    @Test
    @DisplayName("로그아웃 후에는 예전 쿠키로 재발급할 수 없다")
    void reissueFailsAfterLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃은 인증이 필요하다")
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}
