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
 * 뒤 구간의 시작 시각이 같이 맞춰집니다.
 */
@Getter
public enum DebatePhase {

    /** 주제 확인 · 근거 생성 */
    PREP(Duration.ofSeconds(60), false, false),
    /** 주장 (채팅) */
    CHAT_1(Duration.ofSeconds(180), true, false),
    /** 상대 요약 확인 · 반박 준비 */
    REBUTTAL(Duration.ofSeconds(30), false, false),
    /** 반박 (채팅) */
    CHAT_2(Duration.ofSeconds(180), true, false),
    /** 최종변론 1건 제출 */
    FINAL(Duration.ofSeconds(30), false, true),
    /** 판정. 끝이 없는 마지막 구간입니다. */
    JUDGING(null, false, false);

    /** 구간 길이. {@link #JUDGING} 은 끝이 없어 null 입니다. */
    private final Duration duration;
    private final boolean chatAllowed;
    private final boolean finalAllowed;

    DebatePhase(Duration duration, boolean chatAllowed, boolean finalAllowed) {
        this.duration = duration;
        this.chatAllowed = chatAllowed;
        this.finalAllowed = finalAllowed;
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
