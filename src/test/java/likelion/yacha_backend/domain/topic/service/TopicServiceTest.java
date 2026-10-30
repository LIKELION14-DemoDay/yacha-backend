package likelion.yacha_backend.domain.topic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;
import likelion.yacha_backend.domain.topic.dto.TopicResponse;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.exception.TopicErrorCode;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.global.config.JpaAuditingConfig;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@DisplayName("TopicService — 랜덤 주제")
class TopicServiceTest {

    @Autowired
    private TopicRepository topicRepository;

    /** 정해 둔 인덱스를 차례로 돌려주고, 받은 범위(후보 수)를 기록합니다. */
    static class FixedRandom implements RandomGenerator {
        private final Deque<Integer> picks;
        final List<Integer> bounds = new ArrayList<>();

        FixedRandom(Integer... picks) {
            this.picks = new ArrayDeque<>(List.of(picks));
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return picks.isEmpty() ? 0 : picks.poll();
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException();
        }
    }

    private Topic save(Subcategory subcategory, String statement) {
        return topicRepository.save(Topic.create(subcategory, statement, "찬성", "반대"));
    }

    private void assertError(Runnable action, TopicErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    @Test
    @DisplayName("하위 카테고리를 먼저 고른다 — 주제가 10개인 AI 와 1개인 로봇이 같은 확률(후보 2개)로 고려된다")
    void picksSubcategoryFirst() {
        for (int i = 0; i < 10; i++) {
            save(Subcategory.AI, "AI " + i);
        }
        Topic robot = save(Subcategory.ROBOT, "로봇");
        // 하위 카테고리 후보는 [AI, ROBOT] (이름순). 1번 → ROBOT, 그 안에서 0번
        FixedRandom random = new FixedRandom(1, 0);

        TopicResponse picked = new TopicService(topicRepository, random).pickRandom(Category.TECH_AND_FUTURE, List.of());

        assertThat(random.bounds).containsExactly(2, 1);
        assertThat(picked.id()).isEqualTo(robot.getId());
        assertThat(picked.subcategory()).isEqualTo(Subcategory.ROBOT);
    }

    @Test
    @DisplayName("고른 하위 카테고리 안에서 주제를 고르고, 질문 · 찬성 · 반대 문구를 준다")
    void picksTopicInSubcategory() {
        save(Subcategory.AI, "첫째");
        Topic second = topicRepository.save(Topic.create(Subcategory.AI,
                "AI가 그린 그림도 예술이라고 할 수 있는가?", "AI 그림도 예술이다!", "AI 그림은 예술이 아니다!"));

        TopicResponse picked = new TopicService(topicRepository, new FixedRandom(0, 1))
                .pickRandom(Category.TECH_AND_FUTURE, List.of());

        assertThat(picked.id()).isEqualTo(second.getId());
        assertThat(picked.category()).isEqualTo(Category.TECH_AND_FUTURE);
        assertThat(picked.statement()).isEqualTo("AI가 그린 그림도 예술이라고 할 수 있는가?");
        assertThat(picked.agreeText()).isEqualTo("AI 그림도 예술이다!");
        assertThat(picked.disagreeText()).isEqualTo("AI 그림은 예술이 아니다!");
    }

    @Test
    @DisplayName("다음 주제로 넘어가기 — 본 주제를 모두 빼면 남은 주제만 나온다")
    void excludesSeenTopics() {
        Topic first = save(Subcategory.AI, "첫 주제");
        Topic second = save(Subcategory.ROBOT, "두 번째 주제");
        Topic left = save(Subcategory.AI, "남은 주제");
        TopicService service = new TopicService(topicRepository, RandomGenerator.getDefault());

        for (int i = 0; i < 20; i++) {
            assertThat(service.pickRandom(Category.TECH_AND_FUTURE, List.of(first.getId(), second.getId())).id())
                    .isEqualTo(left.getId());
        }
    }

    @Test
    @DisplayName("본 주제를 쌓아 가며 넘기면 다 볼 때까지 같은 주제가 다시 나오지 않고, 다 보면 ALL_TOPICS_SEEN")
    void seesEveryTopicOnce() {
        for (int i = 0; i < 5; i++) {
            save(Subcategory.AI, "AI " + i);
            save(Subcategory.ROBOT, "로봇 " + i);
        }
        TopicService service = new TopicService(topicRepository, RandomGenerator.getDefault());
        List<Long> seen = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            seen.add(service.pickRandom(Category.TECH_AND_FUTURE, seen).id());
        }

        assertThat(seen).doesNotHaveDuplicates().hasSize(10);
        assertError(() -> service.pickRandom(Category.TECH_AND_FUTURE, seen), TopicErrorCode.ALL_TOPICS_SEEN);
    }

    @Test
    @DisplayName("다른 카테고리 · 없는 주제 id 는 무시한다")
    void ignoresUnrelatedExcludes() {
        Topic love = save(Subcategory.LOVE, "관계 주제");
        Topic only = save(Subcategory.AI, "하나뿐인 주제");
        TopicService service = new TopicService(topicRepository, RandomGenerator.getDefault());

        assertThat(service.pickRandom(Category.TECH_AND_FUTURE, List.of(love.getId(), 999_999L)).id())
                .isEqualTo(only.getId());
    }

    @Test
    @DisplayName("다른 카테고리 · 비활성 주제는 뽑지 않는다")
    void onlyActiveInCategory() {
        save(Subcategory.LOVE, "관계 주제");
        save(Subcategory.ROBOT, "내린 주제").deactivate();
        Topic active = save(Subcategory.AI, "남은 주제");
        TopicService service = new TopicService(topicRepository, RandomGenerator.getDefault());

        for (int i = 0; i < 20; i++) {
            assertThat(service.pickRandom(Category.TECH_AND_FUTURE, List.of()).id()).isEqualTo(active.getId());
        }
    }

    @Test
    @DisplayName("카테고리에 주제가 없으면 TOPIC_NOT_FOUND")
    void emptyCategory() {
        save(Subcategory.LOVE, "관계 주제");
        TopicService service = new TopicService(topicRepository, RandomGenerator.getDefault());

        assertError(() -> service.pickRandom(Category.TECH_AND_FUTURE, List.of()), TopicErrorCode.TOPIC_NOT_FOUND);
    }

    @Test
    @DisplayName("주제가 하나뿐인데 그 주제를 빼면 ALL_TOPICS_SEEN (주제가 없는 것과 구분)")
    void onlyTopicExcluded() {
        Topic only = save(Subcategory.AI, "하나뿐인 주제");
        TopicService service = new TopicService(topicRepository, RandomGenerator.getDefault());

        assertError(() -> service.pickRandom(Category.TECH_AND_FUTURE, List.of(only.getId())),
                TopicErrorCode.ALL_TOPICS_SEEN);
    }
}
