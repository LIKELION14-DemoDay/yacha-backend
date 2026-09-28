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

    @ParameterizedTest(name = "시작 후 {0}ms → {1}, {2}초에 끝남")
    @CsvSource({
            "0,      PREP,     60",
            "59999,  PREP,     60",
            "60000,  CHAT_1,   240",
            "239999, CHAT_1,   240",
            "240000, REBUTTAL, 270",
            "269999, REBUTTAL, 270",
            "270000, CHAT_2,   450",
            "449999, CHAT_2,   450",
            "450000, FINAL,    480",
            "479999, FINAL,    480",
    })
    @DisplayName("구간은 시작 시각을 포함하고 끝 시각은 포함하지 않는다")
    void phaseBoundaries(long elapsedMillis, DebatePhase expected, long endsAtSeconds) {
        PhaseState state = DebatePhase.at(STARTED_AT, STARTED_AT.plusNanos(elapsedMillis * 1_000_000));

        assertThat(state.phase()).isEqualTo(expected);
        assertThat(state.endsAt()).isEqualTo(STARTED_AT.plusSeconds(endsAtSeconds));
    }

    @ParameterizedTest(name = "시작 후 {0}초 → JUDGING")
    @CsvSource({"480", "481", "3600"})
    @DisplayName("480초부터는 끝이 없는 JUDGING 이다")
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
    @DisplayName("채팅은 CHAT_1 · CHAT_2, 최종변론은 FINAL 에서만 허용된다")
    void allowedActions() {
        assertThat(DebatePhase.values())
                .filteredOn(DebatePhase::isChatAllowed)
                .containsExactly(DebatePhase.CHAT_1, DebatePhase.CHAT_2);
        assertThat(DebatePhase.values())
                .filteredOn(DebatePhase::isFinalAllowed)
                .containsExactly(DebatePhase.FINAL);
    }

    @Test
    @DisplayName("시작 시각이나 현재 시각이 없으면 계산하지 않는다")
    void rejectsNull() {
        assertThatThrownBy(() -> DebatePhase.at(null, STARTED_AT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DebatePhase.at(STARTED_AT, null)).isInstanceOf(NullPointerException.class);
    }
}
