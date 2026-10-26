package likelion.yacha_backend.infra.llm;

import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.infra.liner.LinerProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liner AI 구현체를 Spring Bean으로 등록한다.
 *
 * AI_MODE=liner일 때:
 *
 * BotWriter     -> LinerBotWriter
 * HintGenerator -> LinerHintGenerator
 * Judge         -> LinerJudge
 *
 * LinerBotWriter는 @Component로 자동 등록한다.
 * 힌트와 판정은 이 설정 클래스에서 등록한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "ai",
        name = "mode",
        havingValue = "liner"
)
public class LinerPartialAiConfig {

    /**
     * Liner 기반 힌트 생성기를 등록한다.
     *
     * 공통 LinerProperties에서
     * API 주소, API 키, 모델 정보를 가져온다.
     */
    @Bean
    public HintGenerator linerHintGenerator(
            LinerProperties properties
    ) {
        return new LinerHintGenerator(properties);
    }

    /**
     * Liner 기반 AI 판정기를 등록한다.
     *
     * 공통 LinerProperties에서
     * API 주소, API 키, 모델 정보를 가져온다.
     */
    @Bean
    public Judge linerJudge(
            LinerProperties properties
    ) {
        return new LinerJudge(properties);
    }
}