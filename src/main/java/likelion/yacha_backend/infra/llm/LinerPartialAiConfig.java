
package likelion.yacha_backend.infra.llm;

import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.domain.ai.PhilosophyWriter;
import likelion.yacha_backend.infra.liner.LinerProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI_MODE=liner일 때 Liner AI 구현체를 등록한다.
 *
 * BotWriter는 LinerBotWriter의 @Component로 등록된다.
 * HintGenerator와 Judge, 1분 철학 PhilosophyWriter는 이 설정에서 등록된다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "ai",
        name = "mode",
        havingValue = "liner"
)
public class LinerPartialAiConfig {

    /**
     * Liner 모드 활성화 시 API 키가 없으면
     * 애플리케이션 시작을 중단한다.
     */
    public LinerPartialAiConfig(LinerProperties properties) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "AI_MODE=liner 사용 시 LINER_API_KEY가 필요합니다."
            );
        }
    }

    @Bean
    public HintGenerator linerHintGenerator(
            LinerProperties properties
    ) {
        return new LinerHintGenerator(properties);
    }

    @Bean
    public Judge linerJudge(
            LinerProperties properties
    ) {
        return new LinerJudge(properties);
    }

    /** 1분 철학 철학자 글 (#98) */
    @Bean
    public PhilosophyWriter linerPhilosophyWriter(
            LinerProperties properties
    ) {
        return new LinerPhilosophyWriter(properties);
    }
}
