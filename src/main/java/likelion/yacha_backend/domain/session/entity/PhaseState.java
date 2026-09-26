package likelion.yacha_backend.domain.session.entity;

import java.time.LocalDateTime;

/**
 * 특정 시각의 토론 구간.
 *
 * @param phase  현재 구간
 * @param endsAt 현재 구간이 끝나는 시각. {@link DebatePhase#JUDGING} 이면 null
 */
public record PhaseState(DebatePhase phase, LocalDateTime endsAt) {
}
