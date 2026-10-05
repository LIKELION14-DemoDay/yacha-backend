package likelion.yacha_backend.domain.topic.repository;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@DisplayName("TopicRepository")
class TopicRepositoryTest {

    @Autowired
    private TopicRepository topicRepository;

    private Topic save(Subcategory subcategory) {
        return topicRepository.save(Topic.create(subcategory, "질문 " + subcategory, "찬성", "반대"));
    }

    @Test
    @DisplayName("주제를 저장하면 질문 · 입장 문구 · 생성 시각이 남는다")
    void saves() {
        Topic saved = topicRepository.saveAndFlush(Topic.create(Subcategory.AI,
                "AI가 그린 그림도 예술이라고 할 수 있는가?", "AI 그림도 예술이다!", "AI 그림은 예술이 아니다!"));

        Topic found = topicRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatement()).isEqualTo("AI가 그린 그림도 예술이라고 할 수 있는가?");
        assertThat(found.getAgreeText()).isEqualTo("AI 그림도 예술이다!");
        assertThat(found.getDisagreeText()).isEqualTo("AI 그림은 예술이 아니다!");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("활성 주제가 있는 하위 카테고리만 중복 없이 준다 (다른 카테고리 · 비활성 제외)")
    void activeSubcategories() {
        save(Subcategory.AI);
        save(Subcategory.AI);
        save(Subcategory.ROBOT);
        save(Subcategory.CLONING).deactivate();
        save(Subcategory.LOVE);

        assertThat(topicRepository.findActiveSubcategories(Category.TECH_AND_FUTURE, null))
                .containsExactlyInAnyOrder(Subcategory.AI, Subcategory.ROBOT);
    }

    @Test
    @DisplayName("빼는 주제가 하위 카테고리의 유일한 주제면 그 하위 카테고리도 빠진다")
    void excludeRemovesLastTopic() {
        save(Subcategory.AI);
        Topic onlyRobot = save(Subcategory.ROBOT);

        assertThat(topicRepository.findActiveSubcategories(Category.TECH_AND_FUTURE, onlyRobot.getId()))
                .containsExactly(Subcategory.AI);
    }

    @Test
    @DisplayName("하위 카테고리 안의 활성 주제를 id 오름차순으로 주고, 빼는 주제는 뺀다")
    void activeTopics() {
        Topic first = save(Subcategory.AI);
        Topic second = save(Subcategory.AI);
        Topic third = save(Subcategory.AI);
        third.deactivate();
        save(Subcategory.ROBOT);

        assertThat(topicRepository.findActiveTopics(Subcategory.AI, null)).extracting(Topic::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(topicRepository.findActiveTopics(Subcategory.AI, first.getId())).extracting(Topic::getId)
                .containsExactly(second.getId());
    }
}
