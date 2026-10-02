package likelion.yacha_backend.global.security;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비회원은 관전 · 게임만 (10/2 회의). 경로 정책은 SecurityConfig 한 곳에 있어서 여기서 경로별로 확인합니다.
 *
 * <p>아직 없는 경로도 넣었습니다. 보안 규칙은 컨트롤러보다 먼저 판단하므로, 허용된 경로는 404 ·
 * 막힌 경로는 403 으로 갈립니다. 404 가 나오면 "보안은 통과했다" 는 뜻입니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("비회원 접근 범위")
class GuestAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private ResultActions getAs(Role role, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(role)));
    }

    private ResultActions postAs(Role role, String url) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, bearer(role)));
    }

    private String bearer(Role role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(999_999L, role);
    }

    @Test
    @DisplayName("게스트도 게임 · 관전 경로(/sessions/**)는 통과한다")
    void guestPassesSessionEndpoints() throws Exception {
        // 없는 세션이라 서비스에서 404 — 보안에서 막히지 않았다는 뜻
        getAs(Role.GUEST, "/api/v1/sessions/1/state")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("게스트도 내 정보 조회 · 주제 조회는 통과한다")
    void guestPassesReadEndpoints() throws Exception {
        getAs(Role.GUEST, "/api/v1/users/me")
                .andExpect(status().is(not(403)));
        getAs(Role.GUEST, "/api/v1/topics/random")
                .andExpect(status().is(not(403)));
    }

    @Test
    @DisplayName("전투 기록(GET /sessions/me)은 /sessions/** 아래지만 회원 전용")
    void battleHistoryIsMemberOnly() throws Exception {
        getAs(Role.GUEST, "/api/v1/sessions/me")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GUEST_NOT_ALLOWED"));
        getAs(Role.USER, "/api/v1/sessions/me")
                .andExpect(status().is(not(403)));
    }

    @Test
    @DisplayName("목록에 없는 경로는 회원 전용 — 새 기능은 따로 막지 않아도 게스트에게 닫힘")
    void unlistedEndpointsAreMemberOnly() throws Exception {
        postAs(Role.GUEST, "/api/v1/friends/invite")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GUEST_NOT_ALLOWED"));
        // 회원은 보안을 통과하고, 없는 경로라 404
        postAs(Role.USER, "/api/v1/friends/invite")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("회원이 권한 없는 경로를 부르면 GUEST_NOT_ALLOWED 가 아니라 FORBIDDEN")
    void memberGetsPlainForbidden() throws Exception {
        getAs(Role.USER, "/api/v1/admin/anything")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
