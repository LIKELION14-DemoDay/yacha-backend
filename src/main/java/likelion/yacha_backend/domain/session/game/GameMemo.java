package likelion.yacha_backend.domain.session.game;

import java.time.LocalDateTime;
import likelion.yacha_backend.domain.session.entity.DebatePhase;

/**
 * 작성 중인 주장 한 건 ({@code PREP} · {@code REBUTTAL}). 참가자마다 구간당 하나이고 덮어씁니다.
 *
 * <p>공개 전에는 본인만 봅니다. 다음 채팅 구간이 시작되면 {@link MessageType#ARGUMENT} 메시지로 공개됩니다.
 *
 * @param phase     작성한 구간
 * @param content   본문. 비우면 빈 문자열이고, 빈 주장은 공개하지 않습니다
 * @param updatedAt 마지막으로 저장한 시각
 */
public record GameMemo(
        DebatePhase phase,
        String content,
        LocalDateTime updatedAt
) {

    /** 본문은 로그에 남기지 않습니다. */
    @Override
    public String toString() {
        return "GameMemo[phase=" + phase + ", length=" + Game.lengthOf(content) + ", updatedAt=" + updatedAt + "]";
    }
}
