package likelion.yacha_backend.domain.session.game;

import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.session.entity.DebatePhase;

/**
 * 양쪽이 작성 구간을 일찍 마쳐 다음 구간으로 넘어갔습니다 ({@link Game#advanceIfAllSubmitted}).
 * 부르는 쪽은 같은 게임 락 안에서 {@code PHASE_CHANGED(phase)} → {@code revealed} 순으로 보냅니다.
 *
 * @param phase    넘어간 구간 ({@code REVEAL} · {@code CHAT})
 * @param endsAt   넘어간 구간이 끝나는 시각
 * @param at       넘어간 시각
 * @param revealed 닫히는 순간 공개한 글 ({@link MessageType#ARGUMENT}, {@code seqNo} 순)
 */
public record PhaseAdvance(DebatePhase phase, LocalDateTime endsAt, LocalDateTime at, List<GameMessage> revealed) {

    public PhaseAdvance {
        revealed = List.copyOf(revealed);
    }
}
