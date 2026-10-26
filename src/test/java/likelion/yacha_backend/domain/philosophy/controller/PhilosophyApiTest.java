package likelion.yacha_backend.domain.philosophy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyBookmark;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyMemo;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophySection;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyArticleRepository;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyBookmarkRepository;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyMemoRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("1분 철학 보기 API")
class PhilosophyApiTest {

    private static final String BASE = "/api/v1/philosophy";

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

    @Autowired
    private PhilosophyMemoRepository memoRepository;

    @Autowired
    private PhilosophyBookmarkRepository bookmarkRepository;

    private final LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private final LocalDate nextMonday = thisMonday.plusWeeks(1);
    private final LocalDate lastMonday = thisMonday.minusWeeks(1);

    private User me;
    private String myToken;

    @BeforeEach
    void setUp() {
        me = member();
        myToken = token(me);
    }

    private User member() {
        String name = "p" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.createMember(name + "@test.com", "encoded", name));
    }

    private String token(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), Role.USER);
    }

    private MockHttpServletRequestBuilder asMe(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, myToken);
    }

    /** 플라톤 · 칸트 글이 달린 글 */
    private PhilosophyArticle article(String statement, LocalDate weekStart, boolean approved) {
        Topic topic = topicRepository.save(Topic.create(Subcategory.LYING, statement, "찬성", "반대"));
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, weekStart);
        article.addContent("플라톤", List.of(
                new PhilosophySection("첫 소제목", "첫 내용"), new PhilosophySection("둘째 소제목", "둘째 내용")));
        article.addContent("칸트", List.of(new PhilosophySection("소제목", "내용")));
        if (approved) {
            article.approve(LocalDateTime.now());
        }
        return articleRepository.saveAndFlush(article);
    }

    @Test
    @DisplayName("게스트는 403 GUEST_NOT_ALLOWED")
    void guestForbidden() throws Exception {
        User guest = userRepository.save(User.createGuest("게스트"));
        String guestToken = "Bearer " + jwtTokenProvider.createAccessToken(guest.getId(), Role.GUEST);

        mockMvc.perform(get(BASE + "/this-week").header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GUEST_NOT_ALLOWED"));
    }

    @Nested
    @DisplayName("이번 주 글")
    class ThisWeek {

        @Test
        @DisplayName("이번 주에 승인된 글만 카드로 보이고, 검토 전 글 · 다음 주 글은 안 보인다")
        void approvedThisWeekOnly() throws Exception {
            PhilosophyArticle first = article("첫 질문", thisMonday, true);
            article("둘째 질문", thisMonday, true);
            article("검토 전", thisMonday, false);
            article("다음 주", nextMonday, true);
            bookmarkRepository.save(PhilosophyBookmark.create(me, first, LocalDateTime.now()));

            mockMvc.perform(asMe(get(BASE + "/this-week")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weekStart").value(thisMonday.toString()))
                    .andExpect(jsonPath("$.data.articles.length()").value(2))
                    .andExpect(jsonPath("$.data.articles[0].id").value(first.getId()))
                    .andExpect(jsonPath("$.data.articles[0].topic.statement").value("첫 질문"))
                    .andExpect(jsonPath("$.data.articles[0].topic.categoryName").value("윤리"))
                    .andExpect(jsonPath("$.data.articles[0].topic.subcategoryName").value("거짓말"))
                    .andExpect(jsonPath("$.data.articles[0].philosophers[0]").value("플라톤"))
                    .andExpect(jsonPath("$.data.articles[0].philosophers[1]").value("칸트"))
                    .andExpect(jsonPath("$.data.articles[0].bookmarked").value(true))
                    .andExpect(jsonPath("$.data.articles[1].topic.statement").value("둘째 질문"))
                    .andExpect(jsonPath("$.data.articles[1].bookmarked").value(false));
        }

        @Test
        @DisplayName("이번 주 글이 하나도 승인되지 않았으면 지난주 글이 그대로 보인다")
        void fallsBackToLastWeek() throws Exception {
            article("지난주", lastMonday, true);
            article("이번 주 검토 전", thisMonday, false);

            mockMvc.perform(asMe(get(BASE + "/this-week")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weekStart").value(lastMonday.toString()))
                    .andExpect(jsonPath("$.data.articles[0].topic.statement").value("지난주"));
        }

        @Test
        @DisplayName("공개된 글이 하나도 없으면 weekStart가 null이고 빈 목록")
        void empty() throws Exception {
            article("다음 주", nextMonday, true);

            mockMvc.perform(asMe(get(BASE + "/this-week")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weekStart").doesNotExist())
                    .andExpect(jsonPath("$.data.articles.length()").value(0));
        }
    }

    @Nested
    @DisplayName("지난 글")
    class Past {

        @Test
        @DisplayName("이번 주보다 앞선 주의 승인된 글만, 최신 주부터 보인다")
        void beforeThisWeek() throws Exception {
            article("이번 주", thisMonday, true);
            article("2주 전", thisMonday.minusWeeks(2), true);
            article("지난주", lastMonday, true);
            article("지난주 검토 전", lastMonday, false);

            mockMvc.perform(asMe(get(BASE + "/past")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.articles.length()").value(2))
                    .andExpect(jsonPath("$.data.articles[0].topic.statement").value("지난주"))
                    .andExpect(jsonPath("$.data.articles[0].weekStart").value(lastMonday.toString()))
                    .andExpect(jsonPath("$.data.articles[1].topic.statement").value("2주 전"))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("한 페이지 20개이고, 남으면 hasNext가 true")
        void paging() throws Exception {
            article("이번 주", thisMonday, true);
            for (int i = 0; i < 21; i++) {
                article("지난 글 " + i, lastMonday, true);
            }

            mockMvc.perform(asMe(get(BASE + "/past")))
                    .andExpect(jsonPath("$.data.articles.length()").value(20))
                    .andExpect(jsonPath("$.data.hasNext").value(true));
            mockMvc.perform(asMe(get(BASE + "/past").param("page", "1")))
                    .andExpect(jsonPath("$.data.articles.length()").value(1))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("page가 음수면 400 VALIDATION_FAILED")
        void negativePage() throws Exception {
            mockMvc.perform(asMe(get(BASE + "/past").param("page", "-1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("상세")
    class Detail {

        @Test
        @DisplayName("철학자 탭 · 소제목 + 내용 · 내 메모 · 북마크 여부가 보인다")
        void detail() throws Exception {
            PhilosophyArticle article = article("행복을 위해 거짓말을 해도 될까?", thisMonday, true);
            memoRepository.save(PhilosophyMemo.create(me, article, "내 생각"));
            bookmarkRepository.save(PhilosophyBookmark.create(me, article, LocalDateTime.now()));

            mockMvc.perform(asMe(get(BASE + "/" + article.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(article.getId()))
                    .andExpect(jsonPath("$.data.topic.statement").value("행복을 위해 거짓말을 해도 될까?"))
                    .andExpect(jsonPath("$.data.philosophers[0].name").value("플라톤"))
                    .andExpect(jsonPath("$.data.philosophers[0].sections[0].subtitle").value("첫 소제목"))
                    .andExpect(jsonPath("$.data.philosophers[0].sections[1].body").value("둘째 내용"))
                    .andExpect(jsonPath("$.data.philosophers[1].name").value("칸트"))
                    .andExpect(jsonPath("$.data.memo").value("내 생각"))
                    .andExpect(jsonPath("$.data.bookmarked").value(true));
        }

        @Test
        @DisplayName("남의 메모 · 북마크는 보이지 않는다")
        void othersNotShown() throws Exception {
            PhilosophyArticle article = article("질문", thisMonday, true);
            User other = member();
            memoRepository.save(PhilosophyMemo.create(other, article, "남의 메모"));
            bookmarkRepository.save(PhilosophyBookmark.create(other, article, LocalDateTime.now()));

            mockMvc.perform(asMe(get(BASE + "/" + article.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memo").doesNotExist())
                    .andExpect(jsonPath("$.data.bookmarked").value(false));
        }

        @Test
        @DisplayName("지난 글도 볼 수 있다")
        void pastArticle() throws Exception {
            article("이번 주", thisMonday, true);
            PhilosophyArticle past = article("지난주", lastMonday, true);

            mockMvc.perform(asMe(get(BASE + "/" + past.getId())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("검토 전 글 · 승인됐어도 아직 시작 안 한 주의 글 · 없는 글은 404 ARTICLE_NOT_FOUND")
        void notPublished() throws Exception {
            PhilosophyArticle pending = article("검토 전", thisMonday, false);
            PhilosophyArticle nextWeek = article("다음 주", nextMonday, true);

            for (Long id : List.of(pending.getId(), nextWeek.getId(), 999_999L)) {
                mockMvc.perform(asMe(get(BASE + "/" + id)))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error.code").value("ARTICLE_NOT_FOUND"));
            }
        }
    }
}
