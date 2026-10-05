package likelion.yacha_backend.domain.session.game;

import java.time.LocalDateTime;
import likelion.yacha_backend.domain.session.entity.DebatePhase;

/**
 * 제출한 주장 · 반론 한 건 ({@code PREP} 주장 · {@code REBUTTAL} 반론). 참가자마다 구간당 하나이고, 다시 제출하면 덮어씁니다.
 *
 * <p>공개 전에는 본인만 봅니다. 공개 시각이 되면 {@link MessageType#ARGUMENT} 메시지로 공개됩니다.
 *
 * @param phase       작성한 구간
 * @param content     본문. 비어 있을 수 있고, 빈 글은 공개하지 않습니다
 * @param submittedAt 마지막으로 제출한 시각
 */
public record GameMemo(
        DebatePhase phase,
        String content,
        LocalDateTime submittedAt
) {

    /** 본문은 로그에 남기지 않습니다. */
    @Override
    public String toString() {
        return "GameMemo[phase=" + phase + ", length=" + Game.lengthOf(content) + ", submittedAt=" + submittedAt + "]";
    }
}
