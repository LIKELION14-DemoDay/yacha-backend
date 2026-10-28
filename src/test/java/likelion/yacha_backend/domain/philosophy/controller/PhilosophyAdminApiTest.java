package likelion.yacha_backend.domain.philosophy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import likelion.yacha_backend.domain.philosophy.entity.PhilosopherContent;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophySection;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyStatus;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyArticleRepository;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 1분 철학 API. 테스트에서는 생성이 요청 스레드에서 바로 돌고(SyncPhilosophyExecutorConfig) 글은 임시 구현이 만듦 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("관리자 · 1분 철학 API")
class PhilosophyAdminApiTest {

    private static final String BASE = "/api/v1/admin/philosophy";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private PhilosophyArticleRepository articleRepository;

    private final LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private final LocalDate nextMonday = thisMonday.plusWeeks(1);

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = token(Role.ADMIN);
        for (int i = 1; i <= 5; i++) {
            topic("주제 " + i);
        }
    }

    private String token(Role role) {
        String name = "a" + UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.createMember(name + "@test.com", "encoded", name));
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), role);
    }

    private Topic topic(String statement) {
        return topicRepository.save(Topic.create(Subcategory.LYING, statement, "찬성", "반대"));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, adminToken);
    }

    /** 옛 글이 하나 달린 글 */
    private PhilosophyArticle article(LocalDate weekStart, boolean approved) {
        PhilosophyArticle article = PhilosophyArticle.createPending(topic("직접 넣은 주제"), weekStart);
        article.addContent("니체", List.of(new PhilosophySection("옛 소제목", "옛 내용")));
        if (approved) {
            article.approve(LocalDateTime.now());
        }
        return articleRepository.saveAndFlush(article);
    }

    @Test
    @DisplayName("관리자가 아니면 403")
    void adminOnly() throws Exception {
        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, token(Role.USER)))
                .andExpect(status().isForbidden());
    }

    @Nested
    @DisplayName("생성 · 목록")
    class Generate {

        @Test
        @DisplayName("주를 빼면 다음 주에 5편을 만들고, 목록에서 철학자 글까지 검토 전으로 보인다")
        void generatesNextWeek() throws Exception {
            mockMvc.perform(asAdmin(post(BASE + "/generate")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.weekStart").value(nextMonday.toString()));

            mockMvc.perform(asAdmin(get(BASE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(5))
                    .andExpect(jsonPath("$.data[0].weekStart").value(nextMonday.toString()))
                    .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.data[0].approvedAt").doesNotExist())
                    .andExpect(jsonPath("$.data[0].topic.statement").exists())
                    .andExpect(jsonPath("$.data[0].philosophers[0].name").value("아리스토텔레스"))
                    .andExpect(jsonPath("$.data[0].philosophers[0].sections[0].subtitle").exists())
                    .andExpect(jsonPath("$.data[0].philosophers[0].sections[0].body").exists());
        }

        @Test
        @DisplayName("목록의 주제에는 카테고리 · 하위 카테고리의 코드와 이름이 함께 온다")
        void topicFields() throws Exception {
            article(nextMonday, false);

            mockMvc.perform(asAdmin(get(BASE).param("weekStart", nextMonday.toString())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].topic.statement").value("직접 넣은 주제"))
                    .andExpect(jsonPath("$.data[0].topic.category").value("ETHICS"))
                    .andExpect(jsonPath("$.data[0].topic.categoryName").value("윤리"))
                    .andExpect(jsonPath("$.data[0].topic.subcategory").value("LYING"))
                    .andExpect(jsonPath("$.data[0].topic.subcategoryName").value("거짓말"))
                    .andExpect(jsonPath("$.data[0].philosophers[0].name").value("니체"))
                    .andExpect(jsonPath("$.data[0].philosophers[0].sections[0].subtitle").value("옛 소제목"));
        }

        @Test
        @DisplayName("런칭 첫 주처럼 이번 주에도 만들 수 있다")
        void thisWeek() throws Exception {
            mockMvc.perform(asAdmin(post(BASE + "/generate").param("weekStart", thisMonday.toString())))
                    .andExpect(status().isAccepted());

            assertThat(articleRepository.countByWeekStart(thisMonday)).isEqualTo(5);
        }

        @Test
        @DisplayName("이미 다 있으면 409 WEEK_ALREADY_FULL")
        void full() throws Exception {
            mockMvc.perform(asAdmin(post(BASE + "/generate"))).andExpect(status().isAccepted());

            mockMvc.perform(asAdmin(post(BASE + "/generate")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("WEEK_ALREADY_FULL"));
        }

        @Test
        @DisplayName("지난 주는 400 PAST_WEEK, 월요일이 아니면 400 INVALID_WEEK_START")
        void invalidWeek() throws Exception {
            mockMvc.perform(asAdmin(post(BASE + "/generate").param("weekStart", thisMonday.minusWeeks(1).toString())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("PAST_WEEK"));
            mockMvc.perform(asAdmin(post(BASE + "/generate").param("weekStart", nextMonday.plusDays(1).toString())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_WEEK_START"));
            mockMvc.perform(asAdmin(get(BASE).param("weekStart", nextMonday.plusDays(1).toString())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_WEEK_START"));
        }
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("승인하면 APPROVED가 되고 승인 시각이 남는다")
        void approves() throws Exception {
            PhilosophyArticle article = article(nextMonday, false);

            mockMvc.perform(asAdmin(post(BASE + "/" + article.getId() + "/approve")))
                    .andExpect(status().isOk());

            PhilosophyArticle found = articleRepository.findById(article.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(PhilosophyStatus.APPROVED);
            assertThat(found.getApprovedAt()).isNotNull();
        }

        @Test
        @DisplayName("없는 글이면 404 ARTICLE_NOT_FOUND")
        void notFound() throws Exception {
            mockMvc.perform(asAdmin(post(BASE + "/999999/approve")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("ARTICLE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("다시 생성 · 빼기")
    class RegenerateDelete {

        @Test
        @DisplayName("다시 생성하면 같은 주제로 글이 바뀌고, 승인돼 있었어도 검토 전으로 돌아간다")
        void regenerates() throws Exception {
            PhilosophyArticle article = article(nextMonday, true);
            Long topicId = article.getTopic().getId();

            mockMvc.perform(asAdmin(post(BASE + "/" + article.getId() + "/regenerate")))
                    .andExpect(status().isAccepted());

            PhilosophyArticle found = articleRepository.findById(article.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(PhilosophyStatus.PENDING);
            assertThat(found.getTopic().getId()).isEqualTo(topicId);
            assertThat(found.getContents()).extracting(PhilosopherContent::getPhilosopherName)
                    .containsExactly("아리스토텔레스", "칸트");
        }

        @Test
        @DisplayName("빼면 글이 지워진다")
        void deletes() throws Exception {
            PhilosophyArticle article = article(nextMonday, false);

            mockMvc.perform(asAdmin(delete(BASE + "/" + article.getId())))
                    .andExpect(status().isOk());

            assertThat(articleRepository.findById(article.getId())).isEmpty();
        }

        @Test
        @DisplayName("공개된 글(승인됐고 그 주가 시작됨)은 다시 생성 · 빼기 모두 409 ARTICLE_ALREADY_PUBLISHED")
        void publishedIsLocked() throws Exception {
            PhilosophyArticle published = article(thisMonday, true);

            mockMvc.perform(asAdmin(post(BASE + "/" + published.getId() + "/regenerate")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ARTICLE_ALREADY_PUBLISHED"));
            mockMvc.perform(asAdmin(delete(BASE + "/" + published.getId())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ARTICLE_ALREADY_PUBLISHED"));
        }

        @Test
        @DisplayName("이번 주 글이라도 검토 전이면 다시 생성할 수 있다 (런칭 첫 주)")
        void pendingThisWeekIsEditable() throws Exception {
            PhilosophyArticle article = article(thisMonday, false);

            mockMvc.perform(asAdmin(post(BASE + "/" + article.getId() + "/regenerate")))
                    .andExpect(status().isAccepted());
        }
    }
}
