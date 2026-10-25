package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import likelion.yacha_backend.domain.ai.JudgeCriterion;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.session.entity.DebateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Verdict — 총점으로 승패 정하기")
class VerdictTest {

    private static final Map<JudgeCriterion, String> SUMMARIES = Arrays.stream(JudgeCriterion.values())
            .collect(() -> new EnumMap<>(JudgeCriterion.class), (map, c) -> map.put(c, c.name()), Map::putAll);

    @Test
    @DisplayName("총점이 높은 쪽이 WIN, 낮은 쪽이 LOSE")
    void higherTotalWins() {
        Verdict verdict = Verdict.of(List.of(7L, 8L), result(7L, 15, 8L, 20));

        assertThat(verdict.resultOf(7L)).isEqualTo(DebateResult.LOSE);
        assertThat(verdict.resultOf(8L)).isEqualTo(DebateResult.WIN);
        assertThat(verdict.winnerParticipantId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("총점이 같으면 둘 다 DRAW, 승자는 null")
    void sameTotalDraws() {
        Verdict verdict = Verdict.of(List.of(7L, 8L), result(7L, 18, 8L, 18));

        assertThat(verdict.results().values()).containsOnly(DebateResult.DRAW);
        assertThat(verdict.winnerParticipantId()).isNull();
    }

    @Test
    @DisplayName("판정 결과에 참가자가 빠져 있으면 예외")
    void missingParticipant() {
        assertThatThrownBy(() -> Verdict.of(List.of(7L, 9L), result(7L, 18, 8L, 18)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static JudgeResult result(Long first, int firstScore, Long second, int secondScore) {
        return new JudgeResult(List.of(
                new JudgeResult.ParticipantScore(first, scores(firstScore)),
                new JudgeResult.ParticipantScore(second, scores(secondScore))), SUMMARIES);
    }

    /** 기준 4개 모두 같은 점수. 총점은 4배입니다. */
    static Map<JudgeCriterion, Integer> scores(int each) {
        Map<JudgeCriterion, Integer> scores = new EnumMap<>(JudgeCriterion.class);
        for (JudgeCriterion criterion : JudgeCriterion.values()) {
            scores.put(criterion, each);
        }
        return scores;
    }
}
