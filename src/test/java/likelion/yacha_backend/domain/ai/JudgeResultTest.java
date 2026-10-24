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

    @Test
    @DisplayName("두 참가자가 한 번씩 채점되지 않으면 만들 수 없다 — 없음 · 한 명 · 세 명 · 같은 참가자 두 번")
    void wrongParticipants() {
        JudgeResult.ParticipantScore first = new JudgeResult.ParticipantScore(7L, scores(20, 18, 15, 19));
        JudgeResult.ParticipantScore second = new JudgeResult.ParticipantScore(8L, scores(15, 14, 16, 13));
        JudgeResult.ParticipantScore third = new JudgeResult.ParticipantScore(9L, scores(10, 10, 10, 10));
        JudgeResult.ParticipantScore firstAgain = new JudgeResult.ParticipantScore(7L, scores(10, 10, 10, 10));

        for (List<JudgeResult.ParticipantScore> scores : List.of(List.<JudgeResult.ParticipantScore>of(),
                List.of(first), List.of(first, second, third), List.of(first, firstAgain))) {
            assertThatThrownBy(() -> new JudgeResult(scores, SUMMARIES))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("기준은 선언 순서로 유지되고, 결과는 바꿀 수 없다")
    void criteriaOrderAndImmutable() {
        // SUMMARIES 는 Map.of 라 순서가 섞인 입력이다
        JudgeResult result = new JudgeResult(List.of(
                new JudgeResult.ParticipantScore(7L, scores(20, 18, 15, 19)),
                new JudgeResult.ParticipantScore(8L, scores(15, 14, 16, 13))), SUMMARIES);

        assertThat(result.summaries().keySet()).containsExactly(JudgeCriterion.values());
        assertThat(result.scoreOf(7L).scores().keySet()).containsExactly(JudgeCriterion.values());
        assertThatThrownBy(() -> result.summaries().put(JudgeCriterion.LOGIC, "x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.scoreOf(7L).scores().put(JudgeCriterion.LOGIC, 0))
                .isInstanceOf(UnsupportedOperationException.class);
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
