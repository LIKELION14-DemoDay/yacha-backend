package likelion.yacha_backend.domain.balancegame.service;

import static likelion.yacha_backend.domain.balancegame.content.Choice.A;
import static likelion.yacha_backend.domain.balancegame.content.Choice.B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import likelion.yacha_backend.domain.balancegame.content.BalanceQuestion;
import likelion.yacha_backend.domain.balancegame.content.BalanceQuestion.Option;
import likelion.yacha_backend.domain.balancegame.content.BalanceQuestions;
import likelion.yacha_backend.domain.balancegame.content.Choice;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("밸런스 게임 유형 계산 — 총점 · 동점 규칙")
class BalanceGameScorerTest {

    private static BalanceQuestion question(PhilosopherType primary, PhilosopherType secondary) {
        return new BalanceQuestion(0, "q", new Option("a", primary, secondary),
                new Option("b", PhilosopherType.EPICURUS, PhilosopherType.EPICTETUS));
    }

    @Test
    @DisplayName("배점표대로 철학자 8명 모두 최대 9점 (+2 세 번 · +1 세 번)")
    void everyPhilosopherMaxNine() {
        Map<PhilosopherType, Integer> max = new EnumMap<>(PhilosopherType.class);
        for (BalanceQuestion q : BalanceQuestions.ALL) {
            for (Option option : List.of(q.a(), q.b())) {
                max.merge(option.primary(), BalanceQuestion.PRIMARY_SCORE, Integer::sum);
                max.merge(option.secondary(), BalanceQuestion.SECONDARY_SCORE, Integer::sum);
            }
        }
        assertThat(BalanceQuestions.ALL).hasSize(12);
        assertThat(max).hasSize(8).allSatisfy((type, score) -> assertThat(score).isEqualTo(9));
    }

    @Test
    @DisplayName("선택지는 화면에서 잘리지 않게 공백 포함 14자 이하")
    void optionTextFitsScreen() {
        for (BalanceQuestion q : BalanceQuestions.ALL) {
            assertThat(List.of(q.a().text(), q.b().text()))
                    .as("%d번 선택지", q.number())
                    .allSatisfy(text -> assertThat(text).hasSizeLessThanOrEqualTo(14));
        }
    }

    @Test
    @DisplayName("총점이 가장 높은 철학자")
    void highestTotal() {
        // 니체 9 · 사르트르 7 · 밀 6 …
        List<Choice> answers = List.of(A, A, A, B, B, A, A, B, B, B, A, A);

        assertThat(BalanceGameScorer.decide(BalanceQuestions.ALL, answers)).isEqualTo(PhilosopherType.NIETZSCHE);
    }

    @Test
    @DisplayName("동점이면 +2 를 더 많이 받은 철학자")
    void tieByPrimaryCount() {
        // 칸트 2+2 (주 점수 2번), 밀 1+1+2 (주 점수 1번, 더 나중)
        List<BalanceQuestion> questions = List.of(
                question(PhilosopherType.KANT, PhilosopherType.MILL),
                question(PhilosopherType.KANT, PhilosopherType.MILL),
                question(PhilosopherType.MILL, PhilosopherType.HOBBES));

        assertThat(BalanceGameScorer.decide(questions, List.of(A, A, A))).isEqualTo(PhilosopherType.KANT);
    }

    @Test
    @DisplayName("+2 횟수도 같으면 가장 나중 질문에서 점수를 받은 철학자, 같은 질문이면 +2 를 받은 쪽")
    void tieByLastQuestion() {
        List<BalanceQuestion> later = List.of(
                question(PhilosopherType.KANT, PhilosopherType.HOBBES),
                question(PhilosopherType.MILL, PhilosopherType.SARTRE));
        assertThat(BalanceGameScorer.decide(later, List.of(A, A))).isEqualTo(PhilosopherType.MILL);

        // 칸트 2+1, 밀 1+2 — 마지막 질문에서 둘 다 점수를 받았고 +2 는 밀
        List<BalanceQuestion> sameQuestion = List.of(
                question(PhilosopherType.KANT, PhilosopherType.MILL),
                question(PhilosopherType.MILL, PhilosopherType.KANT));
        assertThat(BalanceGameScorer.decide(sameQuestion, List.of(A, A))).isEqualTo(PhilosopherType.MILL);
    }

    @Test
    @DisplayName("모두 A 를 고르면 칸트 · 홉스 6점 동점, +2 도 2번씩 → 나중(11번)에 점수를 받은 홉스")
    void allA() {
        assertThat(BalanceGameScorer.decide(BalanceQuestions.ALL, Collections.nCopies(12, A)))
                .isEqualTo(PhilosopherType.HOBBES);
    }

    @Test
    @DisplayName("답 수가 질문 수와 다르면 예외")
    void answerCountMismatch() {
        assertThatThrownBy(() -> BalanceGameScorer.decide(BalanceQuestions.ALL, List.of(A)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
