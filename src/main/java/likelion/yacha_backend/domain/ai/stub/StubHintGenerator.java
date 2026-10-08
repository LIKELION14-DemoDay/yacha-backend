package likelion.yacha_backend.domain.ai.stub;

import java.util.List;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.session.entity.Stance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 임시 힌트 — AI 연동 전까지 쓰는 고정 문장 3개입니다 (#76). {@code ai.mode=stub}(기본값)일 때 등록됩니다.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubHintGenerator implements HintGenerator {

    static final List<String> HINTS = List.of(
            "구체적인 사례를 들어 보세요",
            "상대 입장의 약점을 짚어 보세요",
            "결과와 영향을 생각해 보세요"
    );

    @Override
    public List<String> generate(AiTopic topic, Stance stance) {
        return HINTS;
    }
}
