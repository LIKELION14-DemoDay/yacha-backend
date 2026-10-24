package likelion.yacha_backend.domain.session.service;

import java.util.List;
import likelion.yacha_backend.domain.ai.AiMessage;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameMessage;
import likelion.yacha_backend.domain.session.game.MessageType;

/** 게임 대화를 AI 입력으로 바꿉니다. 판정과 봇 대답이 같은 형식을 씁니다. */
final class AiConversation {

    private AiConversation() {
    }

    /** 공개된 주장 · 반론과 채팅 전체를 {@code seqNo} 순서대로. 공개되지 않은 글(빈 글)은 들어가지 않습니다. */
    static List<AiMessage> of(Game game) {
        return game.messagesAfter(0).stream()
                .map(message -> new AiMessage(message.participantId(), kindOf(message), message.content()))
                .toList();
    }

    private static AiMessage.Kind kindOf(GameMessage message) {
        if (message.type() == MessageType.CHAT) {
            return AiMessage.Kind.CHAT;
        }
        return message.phase() == DebatePhase.PREP ? AiMessage.Kind.ARGUMENT : AiMessage.Kind.REBUTTAL;
    }
}
