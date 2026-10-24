package likelion.yacha_backend.domain.ai;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 판정 출력 (명세 2-6). 승패는 넣지 않습니다 — 총점으로 서버가 정합니다 (높은 쪽 승리, 같으면 무승부).
 *
 * <p>두 참가자가 한 번씩 채점됐는지, 기준 4개가 모두 있고 점수가 0 ~ 25 인지 생성할 때 확인합니다.
 * AI 응답이 형식에 맞지 않으면 여기서 예외가 나므로, 구현체는 응답을 이 타입으로 바꾸는 것만으로 검증이 됩니다.
 * 채점된 참가자가 요청의 참가자인지는 이 타입이 요청을 모르므로 쓰는 쪽이 {@link #scoreOf} 로 확인합니다.
 *
 * <p>기준은 선언 순서(LOGIC → EVIDENCE → REBUTTAL → CONSISTENCY)로 유지됩니다. {@code Map.copyOf} 는
 * 순서를 보장하지 않아 서버를 띄울 때마다 응답 JSON 의 기준 순서가 바뀌므로 {@link EnumMap} 을 감싸서 씁니다.
 *
 * @param scores    참가자별 기준 점수
 * @param summaries 기준마다 판정 내용 한 줄 요약 (결과 화면에 4줄)
 */
public record JudgeResult(List<ParticipantScore> scores, Map<JudgeCriterion, String> summaries) {

    /** 채점되는 참가자 수. 토론은 1:1 입니다. */
    private static final int PARTICIPANT_COUNT = 2;

    public JudgeResult {
        requireAllCriteria(summaries, "summaries");
        summaries.values().forEach(summary -> Objects.requireNonNull(summary, "summary"));
        scores = List.copyOf(scores);
        if (scores.size() != PARTICIPANT_COUNT) {
            throw new IllegalArgumentException("점수는 두 참가자 것이어야 합니다: " + scores.size());
        }
        if (scores.stream().map(ParticipantScore::participantId).distinct().count() != scores.size()) {
            throw new IllegalArgumentException("같은 참가자가 두 번 채점됐습니다: "
                    + scores.stream().map(ParticipantScore::participantId).toList());
        }
        summaries = Collections.unmodifiableMap(new EnumMap<>(summaries));
    }

    /** 참가자의 점수. 없으면 예외입니다. */
    public ParticipantScore scoreOf(Long participantId) {
        return scores.stream()
                .filter(score -> score.participantId().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("판정 결과에 없는 참가자: " + participantId));
    }

    /**
     * @param participantId 참가자 id
     * @param scores        기준별 점수 (0 ~ {@value JudgeCriterion#MAX_SCORE})
     */
    public record ParticipantScore(Long participantId, Map<JudgeCriterion, Integer> scores) {

        public ParticipantScore {
            Objects.requireNonNull(participantId, "participantId");
            requireAllCriteria(scores, "scores");
            scores.values().forEach(score -> {
                if (score == null || score < 0 || score > JudgeCriterion.MAX_SCORE) {
                    throw new IllegalArgumentException("점수는 0 ~ " + JudgeCriterion.MAX_SCORE + " 이어야 합니다: " + score);
                }
            });
            scores = Collections.unmodifiableMap(new EnumMap<>(scores));
        }

        /** 총점 (0 ~ 100) */
        public int total() {
            return scores.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static void requireAllCriteria(Map<JudgeCriterion, ?> map, String name) {
        Objects.requireNonNull(map, name);
        if (map.size() != JudgeCriterion.values().length) {
            throw new IllegalArgumentException(name + " 에 기준 4개가 모두 있어야 합니다: " + map.keySet());
        }
    }
}
