package likelion.yacha_backend.domain.balancegame.service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import likelion.yacha_backend.domain.balancegame.content.BalanceQuestion;
import likelion.yacha_backend.domain.balancegame.content.BalanceQuestion.Option;
import likelion.yacha_backend.domain.balancegame.content.Choice;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;

/**
 * 밸런스 게임 답으로 철학 유형을 정합니다. 고른 선택지의 점수를 철학자별로 더해 가장 높은 철학자입니다.
 *
 * <p>8명 모두 최대 9점으로 배점이 같아 동점이 잦습니다(무작위로 답하면 약 3번 중 1번). 그래서 동점은 아래 순서로 가립니다.
 * 같은 답이면 항상 같은 결과가 나옵니다.
 * <ol>
 *   <li>총점이 높은 철학자</li>
 *   <li>+2(주 점수)를 더 많이 받은 철학자</li>
 *   <li>가장 나중 질문에서 점수를 받은 철학자. 같은 질문이면 +2 를 받은 쪽</li>
 * </ol>
 */
final class BalanceGameScorer {

    private BalanceGameScorer() {
    }

    /** @param answers 질문 순서대로의 답. 질문 수와 같아야 합니다 */
    static PhilosopherType decide(List<BalanceQuestion> questions, List<Choice> answers) {
        if (questions.size() != answers.size()) {
            throw new IllegalArgumentException("답 수가 질문 수와 다릅니다. questions=" + questions.size()
                    + ", answers=" + answers.size());
        }
        Map<PhilosopherType, Tally> tallies = new EnumMap<>(PhilosopherType.class);
        for (int i = 0; i < questions.size(); i++) {
            Option option = questions.get(i).option(answers.get(i));
            // 같은 질문에서는 +2 를 받은 쪽이 나중이 되도록 순번을 매깁니다.
            tallies.computeIfAbsent(option.secondary(), type -> new Tally())
                    .add(BalanceQuestion.SECONDARY_SCORE, false, i * 2);
            tallies.computeIfAbsent(option.primary(), type -> new Tally())
                    .add(BalanceQuestion.PRIMARY_SCORE, true, i * 2 + 1);
        }
        return tallies.entrySet().stream()
                .max(Comparator.comparingInt((Map.Entry<PhilosopherType, Tally> e) -> e.getValue().total)
                        .thenComparingInt(e -> e.getValue().primaryCount)
                        .thenComparingInt(e -> e.getValue().lastScoredOrder))
                .orElseThrow()
                .getKey();
    }

    private static final class Tally {

        private int total;
        private int primaryCount;
        private int lastScoredOrder = -1;

        private void add(int score, boolean primary, int order) {
            total += score;
            if (primary) {
                primaryCount++;
            }
            lastScoredOrder = Math.max(lastScoredOrder, order);
        }
    }
}
