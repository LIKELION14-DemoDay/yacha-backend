package likelion.yacha_backend.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JudgeResult — 판정 출력 형식 검증")
class JudgeResultTest {

    private static final Map<JudgeCriterion, String> SUMMARIES = Map.of(
            JudgeCriterion.LOGIC, "논리", JudgeCriterion.EVIDENCE, "근거",
            JudgeCriterion.REBUTTAL, "반박", JudgeCriterion.CONSISTENCY, "일관성");

    @Test
    @DisplayName("기준 4개 점수의 합이 총점이다")
    void total() {
        JudgeResult.ParticipantScore score = new JudgeResult.ParticipantScore(7L, scores(20, 18, 15, 19));

        assertThat(score.total()).isEqualTo(72);
    }

    @Test
    @DisplayName("참가자 id 로 점수를 찾는다")
    void scoreOf() {
        JudgeResult result = new JudgeResult(List.of(
                new JudgeResult.ParticipantScore(7L, scores(20, 18, 15, 19)),
                new JudgeResult.ParticipantScore(8L, scores(15, 14, 16, 13))), SUMMARIES);

        assertThat(result.scoreOf(8L).total()).isEqualTo(58);
        assertThatThrownBy(() -> result.scoreOf(9L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("점수가 0 ~ 25 를 벗어나면 만들 수 없다")
    void scoreOutOfRange() {
        assertThatThrownBy(() -> new JudgeResult.ParticipantScore(7L, scores(26, 18, 15, 19)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JudgeResult.ParticipantScore(7L, scores(-1, 18, 15, 19)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기준이 하나라도 빠지면 만들 수 없다")
    void missingCriterion() {
        Map<JudgeCriterion, Integer> scores = scores(20, 18, 15, 19);
        scores.remove(JudgeCriterion.CONSISTENCY);
        Map<JudgeCriterion, String> summaries = new EnumMap<>(SUMMARIES);
        summaries.remove(JudgeCriterion.LOGIC);

        assertThatThrownBy(() -> new JudgeResult.ParticipantScore(7L, scores))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JudgeResult(List.of(), summaries))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<JudgeCriterion, Integer> scores(int logic, int evidence, int rebuttal, int consistency) {
        Map<JudgeCriterion, Integer> scores = new EnumMap<>(JudgeCriterion.class);
        scores.put(JudgeCriterion.LOGIC, logic);
        scores.put(JudgeCriterion.EVIDENCE, evidence);
        scores.put(JudgeCriterion.REBUTTAL, rebuttal);
        scores.put(JudgeCriterion.CONSISTENCY, consistency);
        return scores;
    }
}
