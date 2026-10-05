package likelion.yacha_backend.domain.session.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("DebatePhase — 토론 시간표")
class DebatePhaseTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 10, 31, 12, 0, 0);

    private static LocalDateTime afterMillis(long millis) {
        return STARTED_AT.plusNanos(millis * 1_000_000);
    }

    @ParameterizedTest(name = "시작 후 {0}ms → {1}, {2}초에 끝남")
    @CsvSource({
            "0,      PREP,     60",
            "59999,  PREP,     60",
            "60000,  REVEAL,   80",
            "79999,  REVEAL,   80",
            "80000,  REBUTTAL, 140",
            "139999, REBUTTAL, 140",
            "140000, CHAT,     260",
            "259999, CHAT,     260",
    })
    @DisplayName("구간은 시작 시각을 포함하고 끝 시각은 포함하지 않는다")
    void phaseBoundaries(long elapsedMillis, DebatePhase expected, long endsAtSeconds) {
        PhaseState state = DebatePhase.at(STARTED_AT, afterMillis(elapsedMillis));

        assertThat(state.phase()).isEqualTo(expected);
        assertThat(state.endsAt()).isEqualTo(STARTED_AT.plusSeconds(endsAtSeconds));
    }

    @ParameterizedTest(name = "시작 후 {0}초 → JUDGING")
    @CsvSource({"260", "261", "3600"})
    @DisplayName("260초(4분 20초)부터는 끝이 없는 JUDGING 이다")
    void judgingHasNoEnd(long elapsedSeconds) {
        PhaseState state = DebatePhase.at(STARTED_AT, STARTED_AT.plusSeconds(elapsedSeconds));

        assertThat(state.phase()).isEqualTo(DebatePhase.JUDGING);
        assertThat(state.endsAt()).isNull();
    }

    @Test
    @DisplayName("현재 시각이 시작 시각보다 앞서면 PREP 으로 본다")
    void beforeStartIsPrep() {
        PhaseState state = DebatePhase.at(STARTED_AT, STARTED_AT.minusSeconds(3));

        assertThat(state.phase()).isEqualTo(DebatePhase.PREP);
        assertThat(state.endsAt()).isEqualTo(STARTED_AT.plusSeconds(60));
    }

    @Test
    @DisplayName("주장 · 반론 작성은 PREP · REBUTTAL, 채팅은 CHAT 에서만 허용된다")
    void allowedActions() {
        assertThat(DebatePhase.values())
                .filteredOn(DebatePhase::isMemoAllowed)
                .containsExactly(DebatePhase.PREP, DebatePhase.REBUTTAL);
        assertThat(DebatePhase.values())
                .filteredOn(DebatePhase::isChatAllowed)
                .containsExactly(DebatePhase.CHAT);
    }

    @ParameterizedTest(name = "시작 후 {0}ms → {1}")
    @CsvSource({
            "0,      PREP",
            "59999,  PREP",
            "60000,  PREP",
            "62999,  PREP",
            "63000,  ",
            "79999,  ",
            "80000,  REBUTTAL",
            "142999, REBUTTAL",
            "143000, ",
            "260000, ",
    })
    @DisplayName("제출을 받는 작성 구간은 작성 구간 안이거나 끝난 뒤 3초 유예 안이다")
    void memoPhaseAt(long elapsedMillis, DebatePhase expected) {
        assertThat(DebatePhase.memoPhaseAt(STARTED_AT, afterMillis(elapsedMillis))).isEqualTo(expected);
    }

    @Test
    @DisplayName("주장은 63초, 반론은 143초에 공개한다 (작성 구간 끝 + 3초)")
    void revealAt() {
        assertThat(DebatePhase.PREP.revealAt(STARTED_AT)).isEqualTo(STARTED_AT.plusSeconds(63));
        assertThat(DebatePhase.REBUTTAL.revealAt(STARTED_AT)).isEqualTo(STARTED_AT.plusSeconds(143));
        assertThatThrownBy(() -> DebatePhase.CHAT.revealAt(STARTED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "시작 후 {0}ms → {1}")
    @CsvSource({
            "139999, false",
            "140000, false",
            "142999, false",
            "143000, true",
            "259999, true",
            "260000, false",
    })
    @DisplayName("채팅은 CHAT 구간에서 반론이 공개된 143초부터 받는다")
    void isChatOpen(long elapsedMillis, boolean expected) {
        assertThat(DebatePhase.isChatOpen(STARTED_AT, afterMillis(elapsedMillis))).isEqualTo(expected);
    }

    @Test
    @DisplayName("시작 시각이나 현재 시각이 없으면 계산하지 않는다")
    void rejectsNull() {
        assertThatThrownBy(() -> DebatePhase.at(null, STARTED_AT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DebatePhase.at(STARTED_AT, null)).isInstanceOf(NullPointerException.class);
    }
}
