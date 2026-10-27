package likelion.yacha_backend.domain.balancegame.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("밸런스 게임 API — 질문 · 결과 · 건너뛰기 · 공유 · 내 정보")
class BalanceGameApiTest {

    /** 모두 A — 홉스 (BalanceGameScorerTest 참고) */
    private static final String ALL_A = """
            {"answers": ["A","A","A","A","A","A","A","A","A","A","A","A"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    private String guest;

    @BeforeEach
    void createGuest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();
        guest = "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private ResultActions me(String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions submit(String bearer, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/balance-game/result").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions skip(String bearer) throws Exception {
        return mockMvc.perform(post("/api/v1/balance-game/skip").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    @Test
    @DisplayName("질문 12개를 순서대로, 배점 없이 준다")
    void questions() throws Exception {
        mockMvc.perform(get("/api/v1/balance-game/questions").header(HttpHeaders.AUTHORIZATION, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[0].number").value(1))
                .andExpect(jsonPath("$.data[0].question").value("친한 친구가 범죄를 저질렀다는 걸 알았다."))
                .andExpect(jsonPath("$.data[0].optionA").value("관계와 상관없이 신고한다"))
                .andExpect(jsonPath("$.data[0].a").doesNotExist());
    }

    @Test
    @DisplayName("처음엔 일반인이고 게임을 띄워야 함, 결과를 내면 유형 · 문구를 받고 내 정보에 남는다")
    void submitSavesType() throws Exception {
        me(guest)
                .andExpect(jsonPath("$.data.philosopher.type").doesNotExist())
                .andExpect(jsonPath("$.data.philosopher.name").value("일반인"))
                .andExpect(jsonPath("$.data.balanceGameRequired").value(true));

        submit(guest, ALL_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("HOBBES"))
                .andExpect(jsonPath("$.data.name").value("홉스"))
                .andExpect(jsonPath("$.data.alias").value("질서를 세우는 자"))
                .andExpect(jsonPath("$.data.sections.length()").value(3))
                .andExpect(jsonPath("$.data.sections[0].title").value("■ 질서를 위한 규칙"))
                .andExpect(jsonPath("$.data.sections[2].content").isNotEmpty());

        me(guest)
                .andExpect(jsonPath("$.data.philosopher.type").value("HOBBES"))
                .andExpect(jsonPath("$.data.philosopher.name").value("홉스"))
                .andExpect(jsonPath("$.data.balanceGameRequired").value(false));
    }

    @Test
    @DisplayName("건너뛰면 다시 띄우지 않고 일반인, 이미 결과가 있는데 건너뛰면 이전 유형 그대로")
    void skip() throws Exception {
        skip(guest).andExpect(status().isOk());
        me(guest)
                .andExpect(jsonPath("$.data.philosopher.name").value("일반인"))
                .andExpect(jsonPath("$.data.balanceGameRequired").value(false));

        submit(guest, ALL_A);
        skip(guest);
        me(guest).andExpect(jsonPath("$.data.philosopher.type").value("HOBBES"));
    }

    @Test
    @DisplayName("게스트가 가입(승격)해도 유형은 그대로")
    void upgradeKeepsType() throws Exception {
        submit(guest, ALL_A);
        MvcResult upgraded = mockMvc.perform(post("/api/v1/auth/upgrade").header(HttpHeaders.AUTHORIZATION, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "balance@example.com", "password": "password123", "nickname": "멋사"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String member = "Bearer " + JsonPath.read(upgraded.getResponse().getContentAsString(), "$.data.accessToken");

        me(member)
                .andExpect(jsonPath("$.data.isGuest").value(false))
                .andExpect(jsonPath("$.data.philosopher.type").value("HOBBES"));
    }

    @Test
    @DisplayName("답이 12개가 아니면 VALIDATION_FAILED, A · B 가 아니면 PARSING_ERROR, 저장도 안 됨")
    void invalidAnswers() throws Exception {
        submit(guest, "{\"answers\": [\"A\",\"B\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        submit(guest, "{\"answers\": [\"A\",\"A\",\"A\",\"A\",\"A\",\"A\",\"A\",\"A\",\"A\",\"A\",\"A\",\"C\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PARSING_ERROR"));

        me(guest).andExpect(jsonPath("$.data.balanceGameRequired").value(true));
    }

    @Test
    @DisplayName("공유용 유형 설명은 로그인 없이 볼 수 있고, 없는 유형이면 BINDING_ERROR")
    void describeWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/v1/balance-game/philosophers/NIETZSCHE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("니체"))
                .andExpect(jsonPath("$.data.alias").value("기존의 답을 부수는 자"))
                .andExpect(jsonPath("$.data.sections[0].title").value("■ 나만의 기준"))
                .andExpect(jsonPath("$.data.sections[1].title").value("■ 성장을 위한 도전"));
        mockMvc.perform(get("/api/v1/balance-game/philosophers/PLATO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BINDING_ERROR"));
    }

    @Test
    @DisplayName("질문 · 결과 · 건너뛰기는 로그인이 필요하다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/balance-game/questions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/balance-game/skip")).andExpect(status().isUnauthorized());
    }
}
