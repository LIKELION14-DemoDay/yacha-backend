package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import likelion.yacha_backend.domain.auth.repository.RefreshTokenStore;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 에서 패키지가 org.springframework.boot.test.autoconfigure.web.servlet 에서
// 여기로 옮겨졌습니다. 예전 블로그 예제를 복사하면 import 를 못 찾습니다.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("POST /api/v1/auth/guest")
class GuestApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("인증 없이 호출하면 게스트가 생성되고 액세스 토큰이 내려온다")
    void createsGuestWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.nickname").isNotEmpty())
                .andExpect(jsonPath("$.data.isGuest").value(true))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("리프레시 토큰은 HttpOnly 쿠키로만 내려간다")
    void refreshTokenIsSentOnlyAsHttpOnlyCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("SameSite=Lax");
    }

    @Test
    @DisplayName("생성된 사용자는 게스트이고 이메일 · 비밀번호가 없다")
    void createdUserIsGuest() throws Exception {
        long before = userRepository.count();

        mockMvc.perform(post("/api/v1/auth/guest")).andExpect(status().isOk());

        assertThat(userRepository.count()).isEqualTo(before + 1);

        List<User> users = userRepository.findAll();
        User guest = users.get(users.size() - 1);
        assertThat(guest.isGuest()).isTrue();
        assertThat(guest.getEmail()).isNull();
        assertThat(guest.getPassword()).isNull();
        assertThat(guest.getNickname()).isNotBlank();
    }

    @Test
    @DisplayName("발급된 액세스 토큰으로 사용자를 식별할 수 있고, 리프레시 토큰은 저장소에 남는다")
    void issuedTokensAreUsable() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();

        String accessToken = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.accessToken");
        AuthUser authUser = jwtTokenProvider.parseAccessUser(accessToken).orElseThrow();

        assertThat(refreshTokenStore.find(authUser.getUserId())).isPresent();
    }
}
