package likelion.yacha_backend.domain.topic.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("주제 파일 — 읽기 · 검사")
class TopicSeedFileTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("실제 주제 파일(topics/topics.json)은 읽을 수 있고 규칙에 맞는다")
    void actualFile() throws Exception {
        try (InputStream in = new ClassPathResource(TopicSeedFile.LOCATION).getInputStream()) {
            assertThat(TopicSeedFile.read(in, objectMapper)).isNotNull();
        }
    }

    @Test
    @DisplayName("주제를 읽고, active 를 빼면 추첨 대상이다")
    void reads() throws Exception {
        List<TopicSeed> seeds = read("""
                [
                  { "code": "tech-ai-01", "subcategory": "AI", "statement": "AI 그림도 예술인가?" },
                  { "code": "tech-ai-02", "subcategory": "AI", "statement": "AI 에게 투표권을 줘야 하는가?",
                    "active": false }
                ]
                """);

        assertThat(seeds).extracting(TopicSeed::code, TopicSeed::subcategory, TopicSeed::isActive)
                .containsExactly(
                        tuple("tech-ai-01", Subcategory.AI, true),
                        tuple("tech-ai-02", Subcategory.AI, false));
    }

    @Test
    @DisplayName("없는 하위 카테고리면 실패한다")
    void unknownSubcategory() {
        assertThatThrownBy(() -> read("""
                [ { "code": "x-01", "subcategory": "NOPE", "statement": "질문" } ]
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("주제 파일을 읽지 못했습니다");
    }

    @Test
    @DisplayName("빠진 값 · 겹치는 code 와 질문 · 50자 넘는 code 를 한꺼번에 알려 준다")
    void invalid() {
        String longCode = "a".repeat(51);

        assertThatThrownBy(() -> read("""
                [
                  { "code": "a-01", "subcategory": "AI", "statement": "질문 하나" },
                  { "code": "a-01", "subcategory": "AI", "statement": " 질문 하나 " },
                  { "statement": " " },
                  { "code": "%s", "subcategory": "AI", "statement": "질문 둘" }
                ]
                """.formatted(longCode)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2번째 주제(a-01): code 가 다른 주제와 겹칩니다")
                .hasMessageContaining("2번째 주제(a-01): statement 가 다른 주제와 겹칩니다")
                .hasMessageContaining("3번째 주제(null): code 가 없습니다")
                .hasMessageContaining("3번째 주제(null): subcategory 가 없습니다")
                .hasMessageContaining("3번째 주제(null): statement 가 없습니다")
                .hasMessageContaining("4번째 주제(" + longCode + "): code 가 50자를 넘습니다");
    }

    private List<TopicSeed> read(String json) throws Exception {
        return TopicSeedFile.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), objectMapper);
    }
}
