
package likelion.yacha_backend.infra.llm;

import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.ai.Judge;

import org.springframework.beans.factory.annotation.Value;
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
 * LinerBotWriter는 @Component로 자동 등록된다.
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
     * 실제 Liner 힌트 생성기 등록.
     *
     * 힌트 3개를 생성하며
     * 각 힌트는 20자 이내로 제한한다.
     */
    @Bean
    public HintGenerator linerHintGenerator(
            @Value("${LINER_API_KEY:}") String apiKey,
            @Value("${LINER_MODEL:liner-mark}") String model
    ) {
        return new LinerHintGenerator(
                apiKey,
                model
        );
    }

    /**
     * 실제 Liner AI 판정기 등록.
     *
     * 두 참가자를 한 번의 API 호출로 평가하고
     * 네 가지 기준별 점수와 요약을 반환한다.
     */
    @Bean
    public Judge linerJudge(
            @Value("${LINER_API_KEY:}") String apiKey,
            @Value("${LINER_MODEL:liner-mark}") String model
    ) {
        return new LinerJudge(
                apiKey,
                model
        );
    }
}
