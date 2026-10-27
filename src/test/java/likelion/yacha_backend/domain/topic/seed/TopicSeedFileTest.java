package likelion.yacha_backend.domain.topic.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("주제 파일 — 읽기 · 검사")
class TopicSeedFileTest {

    /**
     * 열린 질문에 자주 나오는 의문사. "어떤" 은 "어떤 경우에도 ~인가?" 처럼 예 / 아니오 질문에도 자주 써서 뺍니다.
     * 의문사가 아닌 말(왜곡 · 어떻게든 · 누구나 · 언제나 · 어디든 · 몇몇)은 뺍니다. 그 밖에 잘못 걸리면 문장을 바꿉니다.
     */
    private static final Pattern QUESTION_WORD =
            Pattern.compile("무엇|뭐|왜(?!곡)|어떻게(?!든)|누구(?![나든])|언제(?![나든])|어디(?!든)|얼마나|몇(?!몇)");

    /** "~의 의미는?" 처럼 명사 + 은 / 는 으로 끝나는 열린 질문. "~하는가?" 는 "가?" 로 끝나 걸리지 않습니다. */
    private static final Pattern NOUN_QUESTION_END = Pattern.compile("[은는]\\s*\\?$");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("실제 주제 파일(topics/topics.json)은 읽을 수 있고 규칙에 맞는다")
    void actualFile() throws Exception {
        assertThat(readActualFile()).isNotNull();
    }

    /**
     * 답은 예 / 아니오로만 받으므로 열린 질문은 넣지 않습니다. 문장 규칙으로 보는 거라 선택형 질문 등은 놓칠 수 있어
     * 마지막 확인은 사람이 합니다. 잘못 걸러내도 서버가 뜨지 못하면 안 되므로 서버 시작 검사에는 넣지 않습니다.
     */
    @Test
    @DisplayName("실제 주제 파일의 질문은 모두 예 / 아니오로 답할 수 있는 질문이다")
    void actualFileHasOnlyYesNoQuestions() throws Exception {
        assertThat(readActualFile())
                .allSatisfy(seed -> assertThat(openQuestionReason(seed.statement()))
                        .as("%s: %s", seed.code(), seed.statement())
                        .isNull());
    }

    @Test
    @DisplayName("열린 질문 검사 — 의문사 · 명사 + 은 / 는 끝 · 물음표 없음은 걸러내고, 예 / 아니오 질문은 통과시킨다")
    void openQuestionRules() {
        assertThat(openQuestionReason("다수를 위해 한 사람의 희생을 선택해도 되는가?")).isNull();
        assertThat(openQuestionReason("어떤 경우에도 거짓말은 나쁜가?")).isNull();
        assertThat(openQuestionReason("AI가 그린 그림도 예술이라고 할 수 있을까?")).isNull();
        assertThat(openQuestionReason("언론의 왜곡은 언제나 처벌해야 하는가?")).isNull();
        assertThat(openQuestionReason("누구나 어디든 갈 자유가 있는가?")).isNull();

        assertThat(openQuestionReason("AI 시대 예술의 의미는?")).contains("은 / 는");
        assertThat(openQuestionReason("행복의 조건은 ?")).contains("은 / 는");
        assertThat(openQuestionReason("왜 사람은 거짓말을 하는가?")).contains("왜");
        assertThat(openQuestionReason("좋은 삶이란 무엇인가?")).contains("무엇");
        assertThat(openQuestionReason("거짓말은 나쁘다")).contains("물음표");
    }

    /** 열린 질문으로 보이는 이유. 예 / 아니오 질문이면 null. */
    private static String openQuestionReason(String statement) {
        String text = statement.strip();
        if (!text.endsWith("?")) {
            return "물음표로 끝나지 않습니다";
        }
        var questionWord = QUESTION_WORD.matcher(text);
        if (questionWord.find()) {
            return "의문사 '" + questionWord.group() + "' 가 있습니다";
        }
        if (NOUN_QUESTION_END.matcher(text).find()) {
            return "'~은 / 는?' 으로 끝나는 열린 질문입니다";
        }
        return null;
    }

    private List<TopicSeed> readActualFile() throws Exception {
        try (InputStream in = new ClassPathResource(TopicSeedFile.LOCATION).getInputStream()) {
            return TopicSeedFile.read(in, objectMapper);
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
