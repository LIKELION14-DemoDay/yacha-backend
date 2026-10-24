package likelion.yacha_backend.domain.ai.stub;

import java.util.List;
import likelion.yacha_backend.domain.ai.AiMessage;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.BotWriter;
import likelion.yacha_backend.domain.session.entity.Stance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 임시 봇 — AI 연동 전까지 주제의 입장 문구로 만든 템플릿 글을 씁니다 (#76).
 * {@code ai.mode=stub}(기본값)일 때 등록됩니다.
 *
 * <p>채팅 대답은 준비한 문장을 차례로 돌려 씁니다. 몇 번째인지는 대화에서 봇이 한 채팅 수로 정해,
 * 상태를 따로 두지 않습니다.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubBotWriter implements BotWriter {

    static final List<String> REPLIES = List.of(
            "그 말도 일리는 있지만 저는 생각이 달라요.",
            "근거를 조금 더 구체적으로 말해 줄 수 있나요?",
            "그렇게 보면 반대 경우는 어떻게 설명하죠?",
            "결국 중요한 건 그 결과가 누구에게 좋은가예요.",
            "저는 여전히 제 입장이 더 설득력 있다고 봐요."
    );

    @Override
    public String writeArgument(AiTopic topic, Stance botStance) {
        return "저는 '" + topic.stanceText(botStance) + "' 입장입니다. "
                + "이 문제는 개인의 선택을 넘어 사회 전체에 영향을 주기 때문에, "
                + "장기적인 결과를 따져 보면 이 입장이 더 타당하다고 생각합니다.";
    }

    @Override
    public String writeRebuttal(AiTopic topic, Stance botStance, String opponentArgument) {
        return "상대의 주장은 일부 사례에만 맞는 이야기라고 생각합니다. "
                + "더 넓게 보면 '" + topic.stanceText(botStance) + "' 쪽이 "
                + "많은 사람에게 더 나은 결과를 가져옵니다.";
    }

    @Override
    public String reply(AiTopic topic, Stance botStance, Long botParticipantId, List<AiMessage> conversation) {
        long botChats = conversation.stream()
                .filter(message -> message.kind() == AiMessage.Kind.CHAT)
                .filter(message -> message.participantId().equals(botParticipantId))
                .count();
        return REPLIES.get((int) (botChats % REPLIES.size()));
    }
}
