package likelion.yacha_backend.domain.session.game;

import java.time.LocalDateTime;
import java.util.Set;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.PhaseState;

/**
 * 게임의 지금 시간표 (명세 2-11). {@link Game#timeline()} 이 락 안에서 찍어 주는 값이라 읽는 동안 바뀌지 않습니다.
 *
 * <p>구간은 {@code base} 로 계산합니다. 처음엔 {@code DebateSession.startedAt} 이고, 양쪽이 작성 구간을 일찍 마치면
 * 남은 시간만큼 앞당겨집니다 ({@link Game#advanceIfAllSubmitted}). 뒤 구간 길이는 그대로라 시간표가 통째로 당겨집니다.
 *
 * @param base        구간 계산 기준 시각
 * @param version     시간표 버전. 앞당길 때마다 1 씩 오릅니다. 타이머는 등록할 때의 버전과 다르면 아무것도 하지 않습니다
 * @param closedEarly 일찍 닫힌 작성 구간. 유예 없이 닫히는 순간 공개합니다
 */
public record GameTimeline(LocalDateTime base, long version, Set<DebatePhase> closedEarly) {

    public GameTimeline {
        closedEarly = Set.copyOf(closedEarly);
    }

    public PhaseState phaseAt(LocalDateTime now) {
        return DebatePhase.at(base, now);
    }

    public LocalDateTime startAt(DebatePhase phase) {
        return phase.startAt(base);
    }

    public LocalDateTime endAt(DebatePhase phase) {
        return phase.endAt(base);
    }

    /** 작성 구간의 글을 공개하는 시각. 일찍 닫힌 구간은 닫힌 순간(유예 없음), 아니면 구간 끝 + {@link DebatePhase#SUBMIT_GRACE}. */
    public LocalDateTime revealAt(DebatePhase phase) {
        return closedEarly.contains(phase) ? phase.endAt(base) : phase.revealAt(base);
    }

    /** "최종반론" 안내 시각. */
    public LocalDateTime finalNoticeAt() {
        return DebatePhase.finalNoticeAt(base);
    }
}
