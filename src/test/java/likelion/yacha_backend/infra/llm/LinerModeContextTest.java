package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.ai.BotWriter;
import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.domain.ai.PhilosophyWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * 운영처럼 AI_MODE=liner로 서버 전체가 뜨는지 (API는 부르지 않음)
 * AI 인터페이스 하나라도 Liner 구현이 없으면 그 인터페이스를 쓰는 서비스가 만들어지지 않아 서버가 뜨지 않음
 */
@SpringBootTest(properties = {"ai.mode=liner", "liner.api-key=test-api-key"})
@DisplayName("AI_MODE=liner — 서버 기동")
class LinerModeContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("AI 구현 4개가 모두 Liner 구현으로 등록되고 서버가 뜬다")
    void allLinerImplementations() {
        assertThat(context.getBean(HintGenerator.class)).isInstanceOf(LinerHintGenerator.class);
        assertThat(context.getBean(Judge.class)).isInstanceOf(LinerJudge.class);
        assertThat(context.getBean(BotWriter.class)).isInstanceOf(LinerBotWriter.class);
        assertThat(context.getBean(PhilosophyWriter.class)).isInstanceOf(LinerPhilosophyWriter.class);
    }
}
