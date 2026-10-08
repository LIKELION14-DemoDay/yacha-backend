package likelion.yacha_backend.domain.ai.stub;

import java.util.EnumMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.domain.ai.JudgeCriterion;
import likelion.yacha_backend.domain.ai.JudgeRequest;
import likelion.yacha_backend.domain.ai.JudgeResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 임시 판정 — AI 연동 전까지 기준마다 {@value #MIN_SCORE} ~ 25점을 무작위로 주고, 요약은 고정 문구입니다 (#76).
 * {@code ai.mode=stub}(기본값)일 때 등록됩니다.
 *
 * <p>대화 내용은 보지 않습니다. 결과 화면 막대가 너무 비지 않도록 0점부터가 아니라 {@value #MIN_SCORE}점부터 뽑습니다.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubJudge implements Judge {

    static final int MIN_SCORE = 10;

    static final Map<JudgeCriterion, String> SUMMARIES = Map.of(
            JudgeCriterion.LOGIC, "주장의 흐름이 얼마나 자연스러운지 비교했어요.",
            JudgeCriterion.EVIDENCE, "주장을 뒷받침하는 근거를 비교했어요.",
            JudgeCriterion.REBUTTAL, "상대 주장에 얼마나 잘 반박했는지 비교했어요.",
            JudgeCriterion.CONSISTENCY, "처음 입장을 끝까지 지켰는지 비교했어요."
    );

    private final RandomGenerator random;

    public StubJudge(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        return new JudgeResult(
                request.participants().stream()
                        .map(participant -> new JudgeResult.ParticipantScore(participant.participantId(), randomScores()))
                        .toList(),
                SUMMARIES);
    }

    private Map<JudgeCriterion, Integer> randomScores() {
        Map<JudgeCriterion, Integer> scores = new EnumMap<>(JudgeCriterion.class);
        for (JudgeCriterion criterion : JudgeCriterion.values()) {
            scores.put(criterion, random.nextInt(MIN_SCORE, JudgeCriterion.MAX_SCORE + 1));
        }
        return scores;
    }
}
