package likelion.yacha_backend.domain.topic.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Category · Subcategory — 카테고리 8개와 하위 카테고리 42개")
class SubcategoryTest {

    @Test
    @DisplayName("카테고리는 8개이고 자동 제안 순환 순서로 선언돼 있다")
    void categories() {
        assertThat(Category.values()).containsExactly(
                Category.HUMAN, Category.RELATIONSHIP, Category.ETHICS, Category.SOCIETY,
                Category.LIFE_AND_DEATH, Category.TECH_AND_FUTURE, Category.MONEY_AND_SUCCESS,
                Category.TRUTH_AND_BELIEF);
        assertThat(Category.TECH_AND_FUTURE.getDisplayName()).isEqualTo("기술과 미래");
        assertThat(Category.TRUTH_AND_BELIEF.getDisplayName()).isEqualTo("진실과 믿음");
    }

    @Test
    @DisplayName("하위 카테고리는 42개이고 카테고리별 개수가 명세 표와 같다")
    void subcategoryCounts() {
        assertThat(Subcategory.values()).hasSize(42);
        assertThat(Arrays.stream(Category.values()).map(c -> Subcategory.of(c).size()))
                .containsExactly(5, 6, 5, 5, 5, 5, 5, 6);
    }

    @Test
    @DisplayName("of 는 그 카테고리의 하위 카테고리만 선언 순서대로 준다")
    void ofCategory() {
        assertThat(Subcategory.of(Category.TECH_AND_FUTURE)).containsExactly(
                Subcategory.AI, Subcategory.ROBOT, Subcategory.VIRTUAL_REALITY,
                Subcategory.CLONING, Subcategory.HUMAN_AND_TECH);
        assertThat(Subcategory.of(Category.TRUTH_AND_BELIEF))
                .allSatisfy(sub -> assertThat(sub.getCategory()).isEqualTo(Category.TRUTH_AND_BELIEF));
    }

    @Test
    @DisplayName("주제를 만들면 상위 카테고리는 하위 카테고리로 정해지고 활성 상태다")
    void topicCategoryFromSubcategory() {
        Topic topic = Topic.create(Subcategory.REALITY, "지구가 평평하다는 말, 진실인가?",
                "지구는 평평하다!", "지구는 평평하지 않다!");

        assertThat(topic.getCategory()).isEqualTo(Category.TRUTH_AND_BELIEF);
        assertThat(topic.isActive()).isTrue();

        topic.deactivate();

        assertThat(topic.isActive()).isFalse();
    }
}
