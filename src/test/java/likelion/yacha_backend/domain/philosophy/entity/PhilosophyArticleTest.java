package likelion.yacha_backend.domain.philosophy.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PhilosophyArticle")
class PhilosophyArticleTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);
    private static final List<PhilosophySection> SECTIONS = List.of(new PhilosophySection("소제목", "내용"));

    private final Topic topic = Topic.create(Subcategory.GOOD_AND_EVIL, "질문", "찬성", "반대");

    @Test
    @DisplayName("검토 전 상태로 만들어진다")
    void createsPending() {
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, MONDAY);

        assertThat(article.getStatus()).isEqualTo(PhilosophyStatus.PENDING);
        assertThat(article.isApproved()).isFalse();
        assertThat(article.getApprovedAt()).isNull();
    }

    @Test
    @DisplayName("시작일이 월요일이 아니면 만들 수 없다")
    void rejectsNonMonday() {
        assertThatThrownBy(() -> PhilosophyArticle.createPending(topic, MONDAY.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("글은 붙인 순서대로 탭 순서가 정해진다")
    void addsContentsInOrder() {
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, MONDAY);

        article.addContent("아리스토텔레스", SECTIONS);
        article.addContent("플라톤", SECTIONS);

        assertThat(article.getContents())
                .extracting(PhilosopherContent::getPhilosopherName, PhilosopherContent::getSortOrder)
                .containsExactly(
                        tuple("아리스토텔레스", 0),
                        tuple("플라톤", 1));
    }

    @Test
    @DisplayName("소제목 + 내용이 없는 글은 붙일 수 없다")
    void rejectsEmptySections() {
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, MONDAY);

        assertThatThrownBy(() -> article.addContent("플라톤", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("글이 있으면 승인할 수 있다")
    void approves() {
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, MONDAY);
        article.addContent("플라톤", SECTIONS);

        article.approve(NOW);

        assertThat(article.isApproved()).isTrue();
        assertThat(article.getApprovedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("글이 없으면 승인할 수 없다")
    void cannotApproveWithoutContents() {
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, MONDAY);

        assertThatThrownBy(() -> article.approve(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("글을 지우면 승인도 취소되어 다시 검토해야 한다")
    void clearingContentsCancelsApproval() {
        PhilosophyArticle article = PhilosophyArticle.createPending(topic, MONDAY);
        article.addContent("플라톤", SECTIONS);
        article.approve(NOW);

        article.clearContents();

        assertThat(article.getContents()).isEmpty();
        assertThat(article.getStatus()).isEqualTo(PhilosophyStatus.PENDING);
        assertThat(article.getApprovedAt()).isNull();
    }
}
