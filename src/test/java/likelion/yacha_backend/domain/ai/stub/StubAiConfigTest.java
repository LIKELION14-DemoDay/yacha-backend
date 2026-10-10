package likelion.yacha_backend.domain.ai.stub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import likelion.yacha_backend.domain.ai.BotWriter;
import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.ai.Judge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("ai.mode — 임시 구현 등록 여부")
class StubAiConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RandomGenerator.class, () -> new SplittableRandom(1))
            .withUserConfiguration(StubHintGenerator.class, StubBotWriter.class, StubJudge.class);

    @Test
    @DisplayName("설정이 없으면 임시 구현이 등록된다")
    void defaultStub() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HintGenerator.class);
            assertThat(context).hasSingleBean(BotWriter.class);
            assertThat(context).hasSingleBean(Judge.class);
        });
    }

    @Test
    @DisplayName("stub 이 아니면 임시 구현을 등록하지 않는다 — 실제 구현이 대신 들어간다")
    void otherMode() {
        runner.withPropertyValues("ai.mode=openai").run(context -> {
            assertThat(context).doesNotHaveBean(HintGenerator.class);
            assertThat(context).doesNotHaveBean(BotWriter.class);
            assertThat(context).doesNotHaveBean(Judge.class);
        });
    }
}
