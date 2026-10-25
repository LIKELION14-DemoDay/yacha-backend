package likelion.yacha_backend.domain.session.game;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.session.entity.DebateResult;

/**
 * 판정 결과와 그로부터 정한 승패. 결과 화면 보관 시간 동안 게임 메모리에 둡니다 (명세 2-6).
 *
 * <p>승패는 AI 가 아니라 서버가 정합니다 — 총점이 높은 쪽 {@code WIN}, 같으면 둘 다 {@code DRAW}.
 *
 * @param judgeResult         기준별 점수 · 요약
 * @param results             참가자 id → 승패
 * @param winnerParticipantId 이긴 참가자. 무승부면 null
 */
public record Verdict(JudgeResult judgeResult, Map<Long, DebateResult> results, Long winnerParticipantId) {

    public Verdict {
        Objects.requireNonNull(judgeResult, "judgeResult");
        results = Map.copyOf(results);
    }

    /**
     * 두 참가자의 총점을 비교해 승패를 정합니다.
     *
     * @param participantIds 판정한 두 참가자. 판정 결과에 없으면 예외입니다 (AI 응답이 참가자를 빠뜨린 경우)
     */
    public static Verdict of(List<Long> participantIds, JudgeResult judgeResult) {
        if (participantIds.size() != 2) {
            throw new IllegalArgumentException("참가자는 두 명이어야 합니다: " + participantIds.size());
        }
        Long first = participantIds.get(0);
        Long second = participantIds.get(1);
        int compared = Integer.compare(judgeResult.scoreOf(first).total(), judgeResult.scoreOf(second).total());
        if (compared == 0) {
            return new Verdict(judgeResult, Map.of(first, DebateResult.DRAW, second, DebateResult.DRAW), null);
        }
        Long winner = compared > 0 ? first : second;
        Long loser = compared > 0 ? second : first;
        return new Verdict(judgeResult, Map.of(winner, DebateResult.WIN, loser, DebateResult.LOSE), winner);
    }

    public DebateResult resultOf(Long participantId) {
        return results.get(participantId);
    }
}
