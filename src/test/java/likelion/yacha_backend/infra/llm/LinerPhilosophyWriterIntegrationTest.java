package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.PhilosopherWriting;
import likelion.yacha_backend.domain.philosophy.service.PhilosophyWritingValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 Liner API로 1분 철학 글을 만들어 봄. 프롬프트를 고칠 때 결과를 눈으로 확인하는 용도
 * LINER_REAL_API_TEST=true · LINER_API_KEY가 있을 때만 돎
 */
@DisplayName("Liner 1분 철학 글 실제 API 연동 테스트")
@EnabledIfEnvironmentVariable(named = "LINER_REAL_API_TEST", matches = "true")
class LinerPhilosophyWriterIntegrationTest {

    @Test
    @DisplayName("실제 Liner API로 만든 글이 저장 전 검증을 통과한다")
    void writeRealPhilosophy() {
        String apiKey = System.getenv("LINER_API_KEY");
        assertThat(apiKey).as("LINER_API_KEY 환경변수가 필요합니다.").isNotBlank();

        LinerPhilosophyWriter writer = new LinerPhilosophyWriter(apiKey, "liner-mark");
        AiTopic topic = new AiTopic(
                "행복을 위해 거짓말을 해도 될까?",
                "행복을 위한 거짓말은 괜찮다",
                "거짓말은 어떤 경우에도 안 된다"
        );

        List<PhilosopherWriting> writings = PhilosophyWritingValidator.validate(writer.write(topic));

        for (PhilosopherWriting writing : writings) {
            System.out.println("■ " + writing.philosopherName());
            writing.sections().forEach(section ->
                    System.out.println("  [" + section.subtitle() + "] " + section.body()));
        }
        assertThat(writings).hasSizeBetween(2, 3);
    }
}
