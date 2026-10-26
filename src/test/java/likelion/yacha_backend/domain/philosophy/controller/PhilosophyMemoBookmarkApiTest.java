package likelion.yacha_backend.domain.philosophy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("1분 철학 메모 · 북마크 API")
class PhilosophyMemoBookmarkApiTest {

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

    private User me;
    private String myToken;
    private PhilosophyArticle published;

    @BeforeEach
    void setUp() {
        me = member();
        myToken = "Bearer " + jwtTokenProvider.createAccessToken(me.getId(), Role.USER);
        published = article("공개된 글", thisMonday, true);
    }

    private User member() {
        String name = "b" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.createMember(name + "@test.com", "encoded", name));
    }

    private PhilosophyArticle article(String statement, LocalDate weekStart, boolean approved) {
        Topic topic = topicRepository.save(Topic.create(Subcategory.LYING, statement, "찬성", "반대"));
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, weekStart);
        article.addContent("플라톤", List.of(new PhilosophySection("소제목", "내용")));
        if (approved) {
            article.approve(LocalDateTime.now());
        }
        return articleRepository.saveAndFlush(article);
    }

    private MockHttpServletRequestBuilder asMe(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, myToken);
    }

    private ResultActions saveMemo(Long articleId, String json) throws Exception {
        return mockMvc.perform(asMe(put(BASE + "/" + articleId + "/memo"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Nested
    @DisplayName("메모")
    class Memo {

        @Test
        @DisplayName("앞뒤 공백을 지우고 저장하고, 상세에서 보인다")
        void saves() throws Exception {
            saveMemo(published.getId(), "{\"content\": \"  첫 줄\\n둘째 줄  \"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memo").value("첫 줄\n둘째 줄"));

            mockMvc.perform(asMe(get(BASE + "/" + published.getId())))
                    .andExpect(jsonPath("$.data.memo").value("첫 줄\n둘째 줄"));
        }

        @Test
        @DisplayName("다시 저장하면 새로 만들지 않고 덮어쓴다")
        void overwrites() throws Exception {
            saveMemo(published.getId(), "{\"content\": \"처음\"}").andExpect(status().isOk());
            saveMemo(published.getId(), "{\"content\": \"고친 메모\"}").andExpect(status().isOk());

            assertThat(memoRepository.findByUser_IdAndArticle_Id(me.getId(), published.getId()))
                    .get().extracting(PhilosophyMemo::getContent).isEqualTo("고친 메모");
            assertThat(memoRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("빈 문자열 · 공백만 보내면 메모를 지우고 null을 준다")
        void blankDeletes() throws Exception {
            saveMemo(published.getId(), "{\"content\": \"메모\"}").andExpect(status().isOk());

            saveMemo(published.getId(), "{\"content\": \"   \"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memo").doesNotExist());

            assertThat(memoRepository.findByUser_IdAndArticle_Id(me.getId(), published.getId())).isEmpty();
        }

        @Test
        @DisplayName("1000자까지 저장되고, 넘거나 content가 없으면 400 VALIDATION_FAILED")
        void length() throws Exception {
            saveMemo(published.getId(), "{\"content\": \"" + "가".repeat(1000) + "\"}").andExpect(status().isOk());

            saveMemo(published.getId(), "{\"content\": \"" + "가".repeat(1001) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
            saveMemo(published.getId(), "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("공개되지 않은 글에는 404 ARTICLE_NOT_FOUND")
        void notPublished() throws Exception {
            PhilosophyArticle pending = article("검토 전", thisMonday, false);

            saveMemo(pending.getId(), "{\"content\": \"메모\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("ARTICLE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("북마크")
    class Bookmark {

        @Test
        @DisplayName("추가는 여러 번 불러도 하나만 남고, 해제도 여러 번 불러도 200")
        void idempotent() throws Exception {
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(asMe(put(BASE + "/" + published.getId() + "/bookmark")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.bookmarked").value(true));
            }
            assertThat(bookmarkRepository.existsByUser_IdAndArticle_Id(me.getId(), published.getId())).isTrue();
            assertThat(bookmarkRepository.count()).isEqualTo(1);

            for (int i = 0; i < 2; i++) {
                mockMvc.perform(asMe(delete(BASE + "/" + published.getId() + "/bookmark")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.bookmarked").value(false));
            }
            assertThat(bookmarkRepository.count()).isZero();
        }

        @Test
        @DisplayName("공개되지 않은 글에는 404 ARTICLE_NOT_FOUND")
        void notPublished() throws Exception {
            PhilosophyArticle nextWeek = article("다음 주", thisMonday.plusWeeks(1), true);

            mockMvc.perform(asMe(put(BASE + "/" + nextWeek.getId() + "/bookmark")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("ARTICLE_NOT_FOUND"));
        }

        @Test
        @DisplayName("내 북마크 목록은 북마크한 순서 최신부터, 내 것만 카드로 보인다")
        void myBookmarks() throws Exception {
            PhilosophyArticle older = article("먼저 북마크", thisMonday.minusWeeks(1), true);
            LocalDateTime now = LocalDateTime.now();
            bookmarkRepository.save(PhilosophyBookmark.create(me, older, now.minusDays(1)));
            bookmarkRepository.save(PhilosophyBookmark.create(me, published, now));
            bookmarkRepository.save(PhilosophyBookmark.create(member(), article("남의 북마크", thisMonday, true), now));

            mockMvc.perform(asMe(get(BASE + "/bookmarks")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.articles.length()").value(2))
                    .andExpect(jsonPath("$.data.articles[0].topic.statement").value("공개된 글"))
                    .andExpect(jsonPath("$.data.articles[0].philosophers[0]").value("플라톤"))
                    .andExpect(jsonPath("$.data.articles[0].bookmarked").value(true))
                    .andExpect(jsonPath("$.data.articles[1].topic.statement").value("먼저 북마크"))
                    .andExpect(jsonPath("$.data.articles[1].weekStart").value(thisMonday.minusWeeks(1).toString()))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("내 북마크 목록은 한 페이지 20개이고, 남으면 hasNext가 true")
        void paging() throws Exception {
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < 21; i++) {
                bookmarkRepository.save(PhilosophyBookmark.create(
                        me, article("글 " + i, thisMonday, true), now.minusMinutes(i)));
            }

            mockMvc.perform(asMe(get(BASE + "/bookmarks")))
                    .andExpect(jsonPath("$.data.articles.length()").value(20))
                    .andExpect(jsonPath("$.data.hasNext").value(true));
            mockMvc.perform(asMe(get(BASE + "/bookmarks").param("page", "1")))
                    .andExpect(jsonPath("$.data.articles.length()").value(1))
                    .andExpect(jsonPath("$.data.articles[0].topic.statement").value("글 20"))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("page가 음수면 400 VALIDATION_FAILED")
        void negativePage() throws Exception {
            mockMvc.perform(asMe(get(BASE + "/bookmarks").param("page", "-1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }
}
