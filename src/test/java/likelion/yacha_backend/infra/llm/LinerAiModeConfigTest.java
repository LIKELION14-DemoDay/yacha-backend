
package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import likelion.yacha_backend.domain.ai.BotWriter;
import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.domain.ai.stub.StubBotWriter;
import likelion.yacha_backend.domain.ai.stub.StubHintGenerator;
import likelion.yacha_backend.domain.ai.stub.StubJudge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Liner AI 모드 설정 테스트")
class LinerAiModeConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(
                            RandomGenerator.class,
                            () -> new SplittableRandom(1)
                    )
                    .withUserConfiguration(
                            LinerPartialAiConfig.class,
                            LinerBotWriter.class,
                            StubBotWriter.class,
                            StubHintGenerator.class,
                            StubJudge.class
                    );

    @Test
    @DisplayName("기본 stub 모드에서는 임시 AI 구현 3개가 등록된다")
    void defaultStubMode() {

        runner.run(context -> {

            assertThat(context)
                    .hasSingleBean(BotWriter.class)
                    .hasSingleBean(HintGenerator.class)
                    .hasSingleBean(Judge.class);

            assertThat(context.getBean(BotWriter.class))
                    .isInstanceOf(StubBotWriter.class);

            assertThat(context.getBean(HintGenerator.class))
                    .isInstanceOf(StubHintGenerator.class);

            assertThat(context.getBean(Judge.class))
                    .isInstanceOf(StubJudge.class);

            assertThat(context)
                    .doesNotHaveBean(LinerBotWriter.class)
                    .doesNotHaveBean(LinerHintGenerator.class)
                    .doesNotHaveBean(LinerJudge.class);
        });
    }

    @Test
    @DisplayName("liner 모드에서는 실제 Liner 구현 3개가 등록된다")
    void linerMode() {

        runner.withPropertyValues(
                "ai.mode=liner",
                "LINER_API_KEY=",
                "LINER_MODEL=liner-mark"
        ).run(context -> {

            assertThat(context)
                    .hasSingleBean(BotWriter.class)
                    .hasSingleBean(HintGenerator.class)
                    .hasSingleBean(Judge.class);

            assertThat(context.getBean(BotWriter.class))
                    .isInstanceOf(LinerBotWriter.class);

            assertThat(context.getBean(HintGenerator.class))
                    .isInstanceOf(LinerHintGenerator.class);

            assertThat(context.getBean(Judge.class))
                    .isInstanceOf(LinerJudge.class);

            assertThat(context)
                    .doesNotHaveBean(StubBotWriter.class)
                    .doesNotHaveBean(StubHintGenerator.class)
                    .doesNotHaveBean(StubJudge.class);
        });
    }
}
