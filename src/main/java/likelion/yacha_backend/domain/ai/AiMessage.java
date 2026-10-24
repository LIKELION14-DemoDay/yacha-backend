package likelion.yacha_backend.domain.ai;

import java.util.Objects;

/**
 * AI 에 넘기는 대화 한 건. 공개된 주장 · 반론과 채팅이 {@code seqNo} 순서대로 들어갑니다.
 *
 * @param participantId 보낸 참가자 id
 * @param kind          주장 · 반론 · 채팅
 * @param content       내용
 */
public record AiMessage(Long participantId, Kind kind, String content) {

    public AiMessage {
        Objects.requireNonNull(participantId, "participantId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(content, "content");
    }

    public enum Kind {
        /** PREP 에 쓴 주장 */
        ARGUMENT,
        /** REBUTTAL 에 쓴 반론 */
        REBUTTAL,
        /** CHAT 구간의 채팅 */
        CHAT,
    }
}
