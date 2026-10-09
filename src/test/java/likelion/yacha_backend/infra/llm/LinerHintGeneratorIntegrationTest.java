
package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.session.entity.Stance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@DisplayName("Liner 힌트 실제 API 연동 테스트")
@EnabledIfEnvironmentVariable(
        named = "LINER_REAL_API_TEST",
        matches = "true"
)
class LinerHintGeneratorIntegrationTest {

    @Test
    @DisplayName("실제 Liner API로 힌트 3개를 생성한다")
    void generateRealHints() {

        String apiKey = System.getenv("LINER_API_KEY");

        assertThat(apiKey)
                .as("LINER_API_KEY 환경변수가 필요합니다.")
                .isNotBlank();

        LinerHintGenerator generator =
                new LinerHintGenerator(
                        apiKey,
                        "liner-mark"
                );

        AiTopic topic = new AiTopic(
                "AI가 인간의 일자리를 대체하는 것은 바람직한가?",
                "AI가 인간의 일자리를 대체하는 것은 바람직하다",
                "AI가 인간의 일자리를 대체하는 것은 바람직하지 않다"
        );

        long start = System.nanoTime();

        List<String> hints = generator.generate(
                topic,
                Stance.DISAGREE
        );

        long elapsedMillis =
                (System.nanoTime() - start) / 1_000_000;

        assertThat(hints)
                .hasSize(HintGenerator.HINT_COUNT)
                .doesNotHaveDuplicates();

        for (String hint : hints) {

            assertThat(hint).isNotBlank();

            int length = hint.codePointCount(
                    0,
                    hint.length()
            );

            assertThat(length)
                    .isBetween(
                            1,
                            HintGenerator.HINT_MAX_LENGTH
                    );
        }

        System.out.println("=== 실제 Liner 힌트 ===");

        for (int i = 0; i < hints.size(); i++) {
            System.out.println(
                    (i + 1) + ". " + hints.get(i)
            );
        }

        System.out.println(
                "응답 시간: " + elapsedMillis + "ms"
        );
    }
}
