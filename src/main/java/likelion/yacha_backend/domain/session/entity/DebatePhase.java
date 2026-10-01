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
 * 뒤 구간의 시작 시각이 같이 맞춰집니다. 지금 시간표는 전체 6분입니다.
 *
 * <p>{@link #PREP} · {@link #REBUTTAL} 은 상대에게 보내지 않고 <b>주장을 작성</b>하는 구간입니다. 작성한 주장은
 * 다음 채팅 구간({@link #CHAT_1} · {@link #CHAT_2})이 시작될 때 양쪽 모두에게 공개됩니다 ({@link #argumentPhase()}).
 */
@Getter
public enum DebatePhase {

    /** 주제 확인 · 근거 생성 · 주장 작성 */
    PREP(Duration.ofSeconds(60), false, false, true),
    /** 시작할 때 PREP 주장 공개 → 채팅 */
    CHAT_1(Duration.ofSeconds(90), true, false, false),
    /** 상대 요약 확인 · 반박 작성 */
    REBUTTAL(Duration.ofSeconds(120), false, false, true),
    /** 시작할 때 REBUTTAL 주장 공개 → 채팅 */
    CHAT_2(Duration.ofSeconds(60), true, false, false),
    /** 최종변론 1건 제출 */
    FINAL(Duration.ofSeconds(30), false, true, false),
    /** 판정. 끝이 없는 마지막 구간입니다. */
    JUDGING(null, false, false, false);

    /** 구간 길이. {@link #JUDGING} 은 끝이 없어 null 입니다. */
    private final Duration duration;
    private final boolean chatAllowed;
    private final boolean finalAllowed;
    /** 주장을 작성(덮어쓰기)할 수 있는 구간. 작성 중인 주장은 본인만 봅니다. */
    private final boolean memoAllowed;

    DebatePhase(Duration duration, boolean chatAllowed, boolean finalAllowed, boolean memoAllowed) {
        this.duration = duration;
        this.chatAllowed = chatAllowed;
        this.finalAllowed = finalAllowed;
        this.memoAllowed = memoAllowed;
    }

    /**
     * 이 구간이 시작될 때 공개하는 주장을 작성한 구간. {@link #CHAT_1} 은 {@link #PREP},
     * {@link #CHAT_2} 는 {@link #REBUTTAL} 이고, 나머지는 공개할 주장이 없어 null 입니다.
     */
    public DebatePhase argumentPhase() {
        return switch (this) {
            case CHAT_1 -> PREP;
            case CHAT_2 -> REBUTTAL;
            default -> null;
        };
    }

    /**
     * {@code now} 가 속한 구간과 그 구간이 끝나는 시각.
     *
     * <p>구간은 시작 시각을 포함하고 끝 시각은 포함하지 않습니다. 시작 후 정확히 60초는 {@link #CHAT_1} 입니다.
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
}
