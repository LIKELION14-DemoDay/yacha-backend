package likelion.yacha_backend.domain.session.entity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;

/**
 * 토론 진행 시간표 (API 명세 2-11).
 *
 * <p>현재 구간은 저장하지 않고 {@code now - startedAt} 으로 <b>계산</b>합니다. 서버가 재시작돼도 시각만
 * 있으면 구간이 정확하고, 구간을 갱신하는 쓰기와 메시지 처리가 부딪히지 않습니다.
 * 스케줄러는 구간을 바꾸지 않고 "바뀌었다"고 알리기만 합니다.
 *
 * <p>각 구간의 <b>길이만</b> 적고 시작 시각은 선언 순서대로 누적해 구합니다. 길이 하나를 바꿔도
 * 뒤 구간의 시작 시각이 같이 맞춰집니다. 지금 시간표는 전체 4분 20초입니다.
 *
 * <p>{@link #PREP} 에는 주장, {@link #REBUTTAL} 에는 반론을 <b>작성 · 제출</b>합니다. 작성 구간이 끝난 뒤
 * {@link #SUBMIT_GRACE} 동안은 시간 종료 순간의 자동 제출을 받고, 유예가 끝나면 양쪽 글을 공개합니다
 * ({@link #revealAt}). 주장은 {@link #REVEAL} 화면에서, 반론은 {@link #CHAT} 화면 맨 위에서 보입니다.
 */
@Getter
public enum DebatePhase {

    /** 주장 작성 · 제출 (힌트 보기) */
    PREP(Duration.ofSeconds(60), false, true),
    /** 양쪽 주장 공개 화면 */
    REVEAL(Duration.ofSeconds(20), false, false),
    /** 상대 주장을 보며 반론 작성 · 제출 */
    REBUTTAL(Duration.ofSeconds(60), false, true),
    /** 1:1 채팅. 반론이 공개된 뒤부터 받습니다. 마지막 30초는 "최종반론" 안내만 뜨고 똑같이 채팅입니다 */
    CHAT(Duration.ofSeconds(120), true, false),
    /** 판정. 끝이 없는 마지막 구간입니다. */
    JUDGING(null, false, false);

    /**
     * 작성 구간이 끝난 뒤 그 구간의 제출을 더 받는 시간. 프론트가 타이머가 끝나는 순간 자동 제출하면
     * 서버에는 구간이 끝난 뒤에 도착하므로, 이 유예 안의 제출은 받고 공개는 유예가 끝난 뒤에 합니다.
     */
    public static final Duration SUBMIT_GRACE = Duration.ofSeconds(3);

    /** 구간 길이. {@link #JUDGING} 은 끝이 없어 null 입니다. */
    private final Duration duration;
    private final boolean chatAllowed;
    /** 주장 · 반론을 작성 · 제출하는 구간. 제출한 글은 공개 전까지 본인만 봅니다. */
    private final boolean memoAllowed;

    DebatePhase(Duration duration, boolean chatAllowed, boolean memoAllowed) {
        this.duration = duration;
        this.chatAllowed = chatAllowed;
        this.memoAllowed = memoAllowed;
    }

    /**
     * {@code now} 가 속한 구간과 그 구간이 끝나는 시각.
     *
     * <p>구간은 시작 시각을 포함하고 끝 시각은 포함하지 않습니다. 시작 후 정확히 60초는 {@link #REVEAL} 입니다.
     * 초 단위로 자르지 않고 {@link Duration} 그대로 비교합니다.
     *
     * <p>{@code now} 가 {@code startedAt} 보다 앞서면(서버 간 시계 오차 등) {@link #PREP} 으로 봅니다.
     */
    public static PhaseState at(LocalDateTime startedAt, LocalDateTime now) {
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(now, "now");

        Duration elapsed = Duration.between(startedAt, now);
        Duration phaseEnd = Duration.ZERO;
        for (DebatePhase phase : values()) {
            if (phase.duration == null) {
                return new PhaseState(phase, null);
            }
            phaseEnd = phaseEnd.plus(phase.duration);
            if (elapsed.compareTo(phaseEnd) < 0) {
                return new PhaseState(phase, startedAt.plus(phaseEnd));
            }
        }
        // 마지막 JUDGING 의 duration 이 null 이라 위에서 반드시 끝납니다.
        throw new IllegalStateException("마지막 구간은 끝이 없어야 합니다.");
    }

    /**
     * {@code now} 에 제출을 받는 작성 구간. 작성 구간 안이거나, 작성 구간이 끝난 뒤 {@link #SUBMIT_GRACE} 안이면
     * 그 작성 구간이고, 아니면 null 입니다.
     */
    public static DebatePhase memoPhaseAt(LocalDateTime startedAt, LocalDateTime now) {
        DebatePhase current = at(startedAt, now).phase();
        if (current.memoAllowed) {
            return current;
        }
        for (DebatePhase phase : values()) {
            if (phase.memoAllowed && now.isBefore(phase.revealAt(startedAt)) && !now.isBefore(phase.endAt(startedAt))) {
                return phase;
            }
        }
        return null;
    }

    /** 이 작성 구간의 글을 공개하는 시각 — 구간이 끝나고 {@link #SUBMIT_GRACE} 가 지난 때. */
    public LocalDateTime revealAt(LocalDateTime startedAt) {
        if (!memoAllowed) {
            throw new IllegalStateException("작성 구간이 아닙니다. phase=" + this);
        }
        return endAt(startedAt).plus(SUBMIT_GRACE);
    }

    /** 채팅을 받는가 — {@link #CHAT} 구간이고 반론이 공개된 뒤. 반론이 채팅보다 앞 {@code seqNo} 를 받게 합니다. */
    public static boolean isChatOpen(LocalDateTime startedAt, LocalDateTime now) {
        return at(startedAt, now).phase().chatAllowed && !now.isBefore(REBUTTAL.revealAt(startedAt));
    }

    /** 이 구간이 끝나는 시각. {@link #JUDGING} 은 끝이 없어 쓰지 않습니다. */
    private LocalDateTime endAt(LocalDateTime startedAt) {
        Duration end = Duration.ZERO;
        for (DebatePhase phase : values()) {
            end = end.plus(phase.duration);
            if (phase == this) {
                return startedAt.plus(end);
            }
        }
        throw new IllegalStateException("끝이 없는 구간입니다. phase=" + this);
    }
}
