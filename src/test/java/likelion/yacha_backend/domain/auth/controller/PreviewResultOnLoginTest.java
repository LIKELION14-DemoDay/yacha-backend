package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import likelion.yacha_backend.domain.balancegame.service.PreviewResultCookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 전 온보딩에서 한 밸런스 게임 결과가 로그인할 때 계정에 저장되는지
 * 미리보기 응답의 쿠키를 다음 요청에 그대로 실어 브라우저처럼 흉내 냄
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("로그인할 때 로그인 전 밸런스 게임 결과 저장")
class PreviewResultOnLoginTest {

    /** 모두 A — 홉스 */
    private static final String ALL_A = """
            {"answers": ["A","A","A","A","A","A","A","A","A","A","A","A"]}
            """;
    /** 모두 B — 홉스가 아닌 유형 */
    private static final String ALL_B = """
            {"answers": ["B","B","B","B","B","B","B","B","B","B","B","B"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    private Cookie preview(String answers) throws Exception {
        return mockMvc.perform(post("/api/v1/balance-game/preview").contentType(MediaType.APPLICATION_JSON).content(answers))
                .andReturn().getResponse().getCookie(PreviewResultCookie.COOKIE_NAME);
    }

    private Cookie previewSkip() throws Exception {
        return mockMvc.perform(post("/api/v1/balance-game/preview/skip"))
                .andReturn().getResponse().getCookie(PreviewResultCookie.COOKIE_NAME);
    }

    private static String signupBody(String email) {
        return """
                {"email": "%s", "password": "password123", "nickname": "멋사"}
                """.formatted(email);
    }

    private static String newEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private MvcResult signup(String email, Cookie... cookies) throws Exception {
        var request = post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody(email));
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private static String bearer(MvcResult result) throws Exception {
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private ResultActions me(String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    /** 응답이 미리보기 쿠키를 지웠는지 */
    private static boolean deletesPreviewCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(PreviewResultCookie.COOKIE_NAME);
        return cookie != null && cookie.getMaxAge() == 0;
    }

    @Test
    @DisplayName("회원가입하면 미리보기 결과가 저장되고 쿠키를 지운다")
    void signupSavesAndDeletesCookie() throws Exception {
        MvcResult result = signup(newEmail(), preview(ALL_A));

        me(bearer(result))
                .andExpect(jsonPath("$.data.philosopher.type").value("HOBBES"))
                .andExpect(jsonPath("$.data.balanceGameRequired").value(false));
        assertThat(deletesPreviewCookie(result)).isTrue();
    }

    @Test
    @DisplayName("건너뛰고 가입하면 건너뜀으로 저장돼 홈에서 다시 띄우지 않는다")
    void signupSavesSkipped() throws Exception {
        MvcResult result = signup(newEmail(), previewSkip());

        me(bearer(result))
                .andExpect(jsonPath("$.data.philosopher.type").doesNotExist())
                .andExpect(jsonPath("$.data.philosopher.name").value("일반인"))
                .andExpect(jsonPath("$.data.balanceGameRequired").value(false));
    }

    @Test
    @DisplayName("이미 결과가 있는 회원이 로그인하면 덮어쓰지 않고 쿠키만 지운다")
    void loginKeepsExistingResult() throws Exception {
        String email = newEmail();
        signup(email, preview(ALL_A));
        // 로그인 때 실어 보낼 결과는 홉스가 아니어야 덮어쓰지 않았는지 알 수 있음
        mockMvc.perform(post("/api/v1/balance-game/preview").contentType(MediaType.APPLICATION_JSON).content(ALL_B))
                .andExpect(jsonPath("$.data.type").value(Matchers.not("HOBBES")));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email))
                        .cookie(preview(ALL_B)))
                .andExpect(status().isOk())
                .andReturn();

        me(bearer(login)).andExpect(jsonPath("$.data.philosopher.type").value("HOBBES"));
        assertThat(deletesPreviewCookie(login)).isTrue();
    }

    @Test
    @DisplayName("비회원으로 시작하면 게스트에 저장하고 쿠키는 남겨 둔다, 승격하면 결과는 그대로이고 쿠키를 지운다")
    void guestKeepsCookieThenUpgrade() throws Exception {
        Cookie cookie = preview(ALL_A);

        MvcResult guest = mockMvc.perform(post("/api/v1/auth/guest").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
        me(bearer(guest)).andExpect(jsonPath("$.data.philosopher.type").value("HOBBES"));
        // 게스트가 나중에 소셜로 가입(새 계정)해도 이어지게 쿠키를 지우지 않음
        assertThat(guest.getResponse().getCookie(PreviewResultCookie.COOKIE_NAME)).isNull();

        MvcResult upgraded = mockMvc.perform(post("/api/v1/auth/upgrade").header(HttpHeaders.AUTHORIZATION, bearer(guest))
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody(newEmail()))
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
        me(bearer(upgraded)).andExpect(jsonPath("$.data.philosopher.type").value("HOBBES"));
        assertThat(deletesPreviewCookie(upgraded)).isTrue();
    }

    @Test
    @DisplayName("쿠키가 잘못됐으면 로그인은 그대로 되고 쿠키만 지운다")
    void invalidCookie() throws Exception {
        MvcResult result = signup(newEmail(), new Cookie(PreviewResultCookie.COOKIE_NAME, "forged.value.here"));

        me(bearer(result)).andExpect(jsonPath("$.data.balanceGameRequired").value(true));
        assertThat(deletesPreviewCookie(result)).isTrue();
    }

    @Test
    @DisplayName("쿠키가 없으면 지금처럼 아무것도 하지 않는다")
    void noCookie() throws Exception {
        MvcResult result = signup(newEmail());

        me(bearer(result)).andExpect(jsonPath("$.data.balanceGameRequired").value(true));
        assertThat(result.getResponse().getCookie(PreviewResultCookie.COOKIE_NAME)).isNull();
    }

    @Test
    @DisplayName("토큰 재발급에서는 저장하지 않는다 (로그인할 때만)")
    void refreshDoesNotSave() throws Exception {
        MvcResult signup = signup(newEmail());
        Cookie refresh = signup.getResponse().getCookie("refreshToken");

        MvcResult reissued = mockMvc.perform(post("/api/v1/auth/token/refresh").cookie(refresh, preview(ALL_A)))
                .andExpect(status().isOk())
                .andReturn();

        me(bearer(reissued)).andExpect(jsonPath("$.data.balanceGameRequired").value(true));
        assertThat(reissued.getResponse().getCookie(PreviewResultCookie.COOKIE_NAME)).isNull();
    }
}
