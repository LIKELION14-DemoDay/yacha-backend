package likelion.yacha_backend.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.session.entity.Stance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AiTopic — AI 에 넘기는 입장 설명")
class AiTopicTest {

    @Test
    @DisplayName("입장 설명은 질문과 예 / 아니오 답을 한 문장으로 묶는다 (부정형 질문에서도 어느 쪽인지 알 수 있게)")
    void stanceDescription() {
        AiTopic topic = new AiTopic("죽음을 두려워하지 않아도 되는가?", "예", "아니오");

        assertThat(topic.stanceDescription(Stance.AGREE))
                .isEqualTo("질문 \"죽음을 두려워하지 않아도 되는가?\"에 '예'라고 답하는 입장");
        assertThat(topic.stanceDescription(Stance.DISAGREE))
                .isEqualTo("질문 \"죽음을 두려워하지 않아도 되는가?\"에 '아니오'라고 답하는 입장");
    }
}
