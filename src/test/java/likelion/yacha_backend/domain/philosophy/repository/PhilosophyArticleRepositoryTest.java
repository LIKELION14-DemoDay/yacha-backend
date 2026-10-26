package likelion.yacha_backend.domain.philosophy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import likelion.yacha_backend.domain.philosophy.entity.PhilosopherContent;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophySection;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@DisplayName("PhilosophyArticleRepository")
class PhilosophyArticleRepositoryTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @Autowired
    private PhilosophyArticleRepository articleRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private EntityManager em;

    private Topic topic;

    @BeforeEach
    void setUp() {
        topic = topicRepository.save(Topic.create(Subcategory.GOOD_AND_EVIL, "질문", "찬성", "반대"));
    }

    @Test
    @DisplayName("철학자 글과 소제목 + 내용이 순서대로 저장된다")
    void savesContentsAndSectionsInOrder() {
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, MONDAY);
        article.addContent("아리스토텔레스", List.of(
                new PhilosophySection("첫 소제목", "첫 내용"),
                new PhilosophySection("둘째 소제목", "둘째 내용")));
        article.addContent("플라톤", List.of(new PhilosophySection("소제목", "내용")));
        Long id = articleRepository.save(article).getId();
        em.flush();
        em.clear();

        PhilosophyArticle found = articleRepository.findById(id).orElseThrow();

        assertThat(found.getContents()).extracting(PhilosopherContent::getPhilosopherName)
                .containsExactly("아리스토텔레스", "플라톤");
        assertThat(found.getContents().get(0).getSections()).containsExactly(
                new PhilosophySection("첫 소제목", "첫 내용"),
                new PhilosophySection("둘째 소제목", "둘째 내용"));
    }

    @Test
    @DisplayName("글을 지우고 새로 붙이면 이전 글은 DB 에서도 지워진다")
    void replacesContents() {
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, MONDAY);
        article.addContent("아리스토텔레스", List.of(new PhilosophySection("소제목", "옛 내용")));
        article.addContent("플라톤", List.of(new PhilosophySection("소제목", "옛 내용")));
        Long id = articleRepository.save(article).getId();
        em.flush();
        em.clear();

        PhilosophyArticle loaded = articleRepository.findById(id).orElseThrow();
        loaded.clearContents();
        loaded.addContent("칸트", List.of(new PhilosophySection("소제목", "새 내용")));
        em.flush();
        em.clear();

        PhilosophyArticle found = articleRepository.findById(id).orElseThrow();
        assertThat(found.getContents()).extracting(PhilosopherContent::getPhilosopherName)
                .containsExactly("칸트");
        assertThat(found.getContents().get(0).getSortOrder()).isZero();
        Long contentCount = em.createQuery("select count(c) from PhilosopherContent c", Long.class)
                .getSingleResult();
        assertThat(contentCount).isEqualTo(1);
    }

    @Test
    @DisplayName("한 주에 다른 주제는 여러 편 저장할 수 있다")
    void manyTopicsPerWeek() {
        Topic other = topicRepository.save(Topic.create(Subcategory.LYING, "다른 질문", "찬성", "반대"));

        articleRepository.saveAndFlush(PhilosophyArticle.createPending(topic, MONDAY));
        articleRepository.saveAndFlush(PhilosophyArticle.createPending(other, MONDAY));

        assertThat(articleRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 주에 같은 주제는 한 번만 저장할 수 있다")
    void oneArticlePerWeekAndTopic() {
        articleRepository.saveAndFlush(PhilosophyArticle.createPending(topic, MONDAY));

        assertThatThrownBy(() -> articleRepository.saveAndFlush(PhilosophyArticle.createPending(topic, MONDAY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 주제라도 다른 주에는 다시 쓸 수 있다")
    void sameTopicOtherWeek() {
        articleRepository.saveAndFlush(PhilosophyArticle.createPending(topic, MONDAY));
        articleRepository.saveAndFlush(PhilosophyArticle.createPending(topic, MONDAY.plusWeeks(1)));

        assertThat(articleRepository.count()).isEqualTo(2);
    }
}
