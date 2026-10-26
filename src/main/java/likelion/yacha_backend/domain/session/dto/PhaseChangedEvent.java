package likelion.yacha_backend.domain.session.dto;

import java.time.OffsetDateTime;
import likelion.yacha_backend.domain.session.entity.DebatePhase;

/**
 * 구간이 바뀜 ({@code /topic/sessions/{id}}, 명세 2-4 · 2-11). 남은 시간은 {@code endsAt - serverNow} 로 계산합니다.
 *
 * @param type      항상 {@code PHASE_CHANGED}
 * @param phase     새 구간
 * @param endsAt    새 구간이 끝나는 시각. {@code JUDGING} 은 끝이 없어 null
 * @param serverNow 서버가 보낸 시각
 */
public record PhaseChangedEvent(String type, DebatePhase phase, OffsetDateTime endsAt, OffsetDateTime serverNow) {

    public static PhaseChangedEvent of(DebatePhase phase, OffsetDateTime endsAt, OffsetDateTime serverNow) {
        return new PhaseChangedEvent("PHASE_CHANGED", phase, endsAt, serverNow);
    }
}
