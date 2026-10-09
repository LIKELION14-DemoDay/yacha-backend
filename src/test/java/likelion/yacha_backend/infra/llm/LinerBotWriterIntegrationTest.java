
package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.session.entity.Stance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@DisplayName("Liner 실제 API 연동 테스트")
@EnabledIfEnvironmentVariable(
        named = "LINER_REAL_API_TEST",
        matches = "true"
)
class LinerBotWriterIntegrationTest {

    @Test
    @DisplayName("실제 Liner API로 토론 주장을 생성한다")
    void generateRealArgument() {

        String apiKey = System.getenv("LINER_API_KEY");

        assertThat(apiKey)
                .as("LINER_API_KEY 환경변수가 필요합니다.")
                .isNotBlank();

        LinerBotWriter writer = new LinerBotWriter(
                apiKey,
                "liner-mark"
        );

        AiTopic topic = new AiTopic(
                "AI가 인간의 일자리를 대체하는 것은 바람직한가?",
                "AI가 인간의 일자리를 대체하는 것은 바람직하다",
                "AI가 인간의 일자리를 대체하는 것은 바람직하지 않다"
        );

        String result = writer.writeArgument(
                topic,
                Stance.DISAGREE
        );

        assertThat(result).isNotBlank();

        int length = result.codePointCount(
                0,
                result.length()
        );

        assertThat(length)
                .isBetween(1, 200);
    }
}
