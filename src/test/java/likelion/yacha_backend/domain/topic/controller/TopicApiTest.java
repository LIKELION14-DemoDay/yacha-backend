package likelion.yacha_backend.domain.topic.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("주제 API — /topics/random · /categories")
class TopicApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TopicRepository topicRepository;

    private ResultActions getAs(Role role, String url) throws Exception {
        return mockMvc.perform(get(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(1L, role)));
    }

    @Test
    @DisplayName("랜덤 주제를 질문 · 찬성 · 반대 문구와 함께 준다 (게스트 토큰도 가능)")
    void random() throws Exception {
        Topic topic = topicRepository.save(Topic.create(Subcategory.REALITY,
                "지구가 평평하다는 말, 진실인가?", "지구는 평평하다!", "지구는 평평하지 않다!"));

        getAs(Role.GUEST, "/api/v1/topics/random?category=TRUTH_AND_BELIEF")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(topic.getId()))
                .andExpect(jsonPath("$.data.category").value("TRUTH_AND_BELIEF"))
                .andExpect(jsonPath("$.data.subcategory").value("REALITY"))
                .andExpect(jsonPath("$.data.statement").value("지구가 평평하다는 말, 진실인가?"))
                .andExpect(jsonPath("$.data.agreeText").value("지구는 평평하다!"))
                .andExpect(jsonPath("$.data.disagreeText").value("지구는 평평하지 않다!"));
    }

    @Test
    @DisplayName("방금 본 주제를 빼면 남는 게 없을 때 TOPIC_NOT_FOUND (404)")
    void excludeLeavesNothing() throws Exception {
        Topic topic = topicRepository.save(Topic.create(Subcategory.REALITY, "질문", "찬성", "반대"));

        getAs(Role.USER, "/api/v1/topics/random?category=TRUTH_AND_BELIEF&exclude=" + topic.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TOPIC_NOT_FOUND"));
    }

    @Test
    @DisplayName("카테고리 값이 잘못되면 BINDING_ERROR, 빠지면 400")
    void invalidCategory() throws Exception {
        getAs(Role.USER, "/api/v1/topics/random?category=FUTURE_TECH")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BINDING_ERROR"));
        getAs(Role.USER, "/api/v1/topics/random")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("랜덤 주제는 토큰이 없으면 401")
    void randomNeedsToken() throws Exception {
        mockMvc.perform(get("/api/v1/topics/random?category=HUMAN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("카테고리 목록은 토큰 없이 8개와 하위 카테고리를 순환 순서로 준다")
    void categories() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data[0].code").value("HUMAN"))
                .andExpect(jsonPath("$.data[0].name").value("인간"))
                .andExpect(jsonPath("$.data[0].subcategories.length()").value(5))
                .andExpect(jsonPath("$.data[0].subcategories[0].code").value("HUMAN_NATURE"))
                .andExpect(jsonPath("$.data[0].subcategories[0].name").value("인간 본성"))
                .andExpect(jsonPath("$.data[5].code").value("TECH_AND_FUTURE"))
                .andExpect(jsonPath("$.data[5].name").value("기술과 미래"))
                .andExpect(jsonPath("$.data[7].code").value("TRUTH_AND_BELIEF"))
                .andExpect(jsonPath("$.data[7].subcategories.length()").value(6));
    }
}
