package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.ai.PhilosophyWriter;
import likelion.yacha_backend.domain.ai.stub.StubPhilosophyWriter;
import likelion.yacha_backend.infra.liner.LinerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 1분 철학 글 생성기가 ai.mode마다 하나만 등록되는지. 없으면 PhilosophyGenerationService가 뜨지 못함 */
@DisplayName("ai.mode — 1분 철학 글 생성기 등록")
class LinerPhilosophyWriterConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(LinerProperties.class,
                    () -> new LinerProperties("https://platform.liner.com", "test-api-key", "liner-mark"))
            .withUserConfiguration(LinerPartialAiConfig.class, StubPhilosophyWriter.class);

    @Test
    @DisplayName("기본 stub 모드에서는 임시 구현이 등록된다")
    void stubMode() {
        runner.run(context -> assertThat(context.getBean(PhilosophyWriter.class))
                .isInstanceOf(StubPhilosophyWriter.class));
    }

    @Test
    @DisplayName("liner 모드에서는 Liner 구현이 등록된다")
    void linerMode() {
        runner.withPropertyValues("ai.mode=liner").run(context -> assertThat(context.getBean(PhilosophyWriter.class))
                .isInstanceOf(LinerPhilosophyWriter.class));
    }
}
