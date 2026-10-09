
package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.session.entity.Stance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Liner 힌트 생성 단위 테스트")
class LinerHintGeneratorTest {

    private static final AiTopic TOPIC = new AiTopic(
            "AI가 인간의 일자리를 대체하는 것은 바람직한가?",
            "AI가 인간의 일자리를 대체하는 것은 바람직하다",
            "AI가 인간의 일자리를 대체하는 것은 바람직하지 않다"
    );

    @Test
    @DisplayName("토론 주제와 입장을 전달하고 힌트 3개를 반환한다")
    void generateThreeHints() {
        AtomicReference<String> capturedInput =
                new AtomicReference<>();

        LinerHintGenerator generator =
                new LinerHintGenerator("", "liner-mark") {

                    @Override
                    protected String chat(
                            String systemPrompt,
                            String userContent,
                            String logLabel
                    ) {
                        capturedInput.set(userContent);

                        return """
                                고용 감소 우려
                                재교육 부담
                                소득 격차 확대
                                """;
                    }
                };

        List<String> hints = generator.generate(
                TOPIC,
                Stance.DISAGREE
        );

        assertThat(hints).containsExactly(
                "고용 감소 우려",
                "재교육 부담",
                "소득 격차 확대"
        );

        assertThat(capturedInput.get())
                .contains(TOPIC.statement())
                .contains(TOPIC.disagreeText());
    }

    @Test
    @DisplayName("응답에 번호가 있으면 제거한다")
    void removeNumbering() {
        LinerHintGenerator generator = fakeGenerator(
                """
                1. 고용 감소
                2) 재교육 비용
                - 소득 불균형
                """
        );

        List<String> hints = generator.generate(
                TOPIC,
                Stance.AGREE
        );

        assertThat(hints).containsExactly(
                "고용 감소",
                "재교육 비용",
                "소득 불균형"
        );
    }

    @Test
    @DisplayName("힌트가 3개가 아니면 예외를 발생시킨다")
    void invalidHintCount() {
        LinerHintGenerator generator = fakeGenerator(
                """
                고용 감소
                재교육 비용
                """
        );

        assertThatThrownBy(
                () -> generator.generate(TOPIC, Stance.AGREE)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("힌트가 20자를 초과하면 예외를 발생시킨다")
    void hintTooLong() {
        LinerHintGenerator generator = fakeGenerator(
                "가".repeat(21)
                        + "\n재교육 비용\n소득 불균형"
        );

        assertThatThrownBy(
                () -> generator.generate(TOPIC, Stance.AGREE)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("중복된 힌트가 있으면 예외를 발생시킨다")
    void duplicateHints() {
        LinerHintGenerator generator = fakeGenerator(
                """
                고용 감소
                고용 감소
                소득 불균형
                """
        );

        assertThatThrownBy(
                () -> generator.generate(TOPIC, Stance.AGREE)
        ).isInstanceOf(IllegalStateException.class);
    }

    private LinerHintGenerator fakeGenerator(String response) {
        return new LinerHintGenerator("", "liner-mark") {

            @Override
            protected String chat(
                    String systemPrompt,
                    String userContent,
                    String logLabel
            ) {
                return response;
            }
        };
    }
}
