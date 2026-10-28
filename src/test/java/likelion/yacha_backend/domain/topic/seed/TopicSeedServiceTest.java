package likelion.yacha_backend.domain.topic.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, TopicSeedService.class})
@DisplayName("주제 파일과 DB 맞추기")
class TopicSeedServiceTest {

    @Autowired
    private TopicSeedService topicSeedService;

    @Autowired
    private TopicRepository topicRepository;

    @Test
    @DisplayName("파일에만 있는 주제를 넣고, 입장 문구는 예 · 아니오다")
    void creates() {
        TopicSeedService.Result result = topicSeedService.sync(List.of(
                seed("tech-ai-01", Subcategory.AI, "AI 그림도 예술인가?", null),
                seed("tech-ai-02", Subcategory.AI, "AI 에게 투표권을?", false)));

        assertThat(result).isEqualTo(new TopicSeedService.Result(2, 0, 0));
        Topic first = find("tech-ai-01");
        assertThat(first.getCategory()).isEqualTo(Category.TECH_AND_FUTURE);
        assertThat(first.getStatement()).isEqualTo("AI 그림도 예술인가?");
        assertThat(first.getAgreeText()).isEqualTo("예");
        assertThat(first.getDisagreeText()).isEqualTo("아니오");
        assertThat(first.isActive()).isTrue();
        assertThat(find("tech-ai-02").isActive()).isFalse();
    }

    @Test
    @DisplayName("같은 code 면 파일 내용으로 고치고 id 는 그대로다. 바뀐 게 없으면 수정으로 세지 않는다")
    void updates() {
        topicSeedService.sync(List.of(seed("tech-ai-01", Subcategory.AI, "예전 질문", null),
                seed("tech-ai-02", Subcategory.AI, "그대로인 질문", null)));
        Long id = find("tech-ai-01").getId();

        TopicSeedService.Result result = topicSeedService.sync(List.of(
                seed("tech-ai-01", Subcategory.LOVE, "새 질문", null),
                seed("tech-ai-02", Subcategory.AI, "그대로인 질문", null)));

        assertThat(result).isEqualTo(new TopicSeedService.Result(0, 1, 0));
        Topic updated = find("tech-ai-01");
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getStatement()).isEqualTo("새 질문");
        assertThat(updated.getSubcategory()).isEqualTo(Subcategory.LOVE);
        assertThat(updated.getCategory()).isEqualTo(Subcategory.LOVE.getCategory());
    }

    @Test
    @DisplayName("파일에서 빠진 주제는 지우지 않고 내리고, 다시 넣으면 올린다")
    void deactivatesRemoved() {
        topicSeedService.sync(List.of(seed("tech-ai-01", Subcategory.AI, "질문", null),
                seed("tech-ai-02", Subcategory.AI, "남는 질문", null)));
        List<TopicSeed> withoutFirst = List.of(seed("tech-ai-02", Subcategory.AI, "남는 질문", null));

        assertThat(topicSeedService.sync(withoutFirst)).isEqualTo(new TopicSeedService.Result(0, 0, 1));
        assertThat(find("tech-ai-01").isActive()).isFalse();
        assertThat(topicSeedService.sync(withoutFirst)).isEqualTo(new TopicSeedService.Result(0, 0, 0));

        assertThat(topicSeedService.sync(List.of(seed("tech-ai-01", Subcategory.AI, "질문", null),
                seed("tech-ai-02", Subcategory.AI, "남는 질문", null))))
                .isEqualTo(new TopicSeedService.Result(0, 1, 0));
        assertThat(find("tech-ai-01").isActive()).isTrue();
    }

    @Test
    @DisplayName("파일이 비었는데 DB 에 파일 주제가 있으면 아무것도 내리지 않고 실패한다")
    void rejectsEmptyFileWhenSeeded() {
        topicSeedService.sync(List.of(seed("tech-ai-01", Subcategory.AI, "질문", null)));

        assertThatThrownBy(() -> topicSeedService.sync(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("파일 주제가 1건");
        assertThat(find("tech-ai-01").isActive()).isTrue();
    }

    @Test
    @DisplayName("DB 에 파일 주제가 없으면 빈 파일은 아무 일도 하지 않는다 (주제를 채우기 전)")
    void emptyFileBeforeSeeding() {
        assertThat(topicSeedService.sync(List.of())).isEqualTo(new TopicSeedService.Result(0, 0, 0));
    }

    @Test
    @DisplayName("code 가 없는 주제(파일 밖에서 넣은 것)는 건드리지 않는다")
    void keepsTopicsWithoutCode() {
        Topic manual = topicRepository.save(Topic.create(Subcategory.AI, "직접 넣은 질문", "찬성", "반대"));

        topicSeedService.sync(List.of(seed("tech-ai-01", Subcategory.AI, "질문", null)));

        Topic found = topicRepository.findById(manual.getId()).orElseThrow();
        assertThat(found.getCode()).isNull();
        assertThat(found.isActive()).isTrue();
        assertThat(found.getStatement()).isEqualTo("직접 넣은 질문");
    }

    private Topic find(String code) {
        return topicRepository.findAllByCodeIsNotNull().stream()
                .filter(topic -> topic.getCode().equals(code))
                .findFirst().orElseThrow();
    }

    private static TopicSeed seed(String code, Subcategory subcategory, String statement, Boolean active) {
        return new TopicSeed(code, subcategory, statement, active);
    }
}
