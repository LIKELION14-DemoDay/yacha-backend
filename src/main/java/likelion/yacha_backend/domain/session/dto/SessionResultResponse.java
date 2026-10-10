package likelion.yacha_backend.domain.session.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import likelion.yacha_backend.domain.ai.JudgeCriterion;
import likelion.yacha_backend.domain.session.entity.DebateResult;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.JudgeStatus;

/**
 * {@code GET /sessions/{id}/result} — 결과 화면 (명세 2-6).
 *
 * <p>판정 중이면 {@code status} 만 있습니다. 점수 · 요약은 결과 화면 보관 시간 동안만 있고, 그 뒤와 몰수패는
 * 승패만 있습니다({@code criteria} · {@code scores} · {@code total} 이 null).
 *
 * @param status              {@code PENDING} 판정 중 / {@code READY} 결과 있음 / {@code FAILED} 실패 · 결과 없음
 * @param finishReason        종료 사유. {@code PENDING} 이면 null
 * @param winnerParticipantId 이긴 참가자. 무승부거나 결과가 없으면 null
 * @param criteria            기준 4개의 한 줄 요약 (논리 · 근거 · 반박 · 일관성 순서)
 * @param participants        두 참가자
 */
public record SessionResultResponse(
        JudgeStatus status,
        FinishReason finishReason,
        Long winnerParticipantId,
        List<CriterionResponse> criteria,
        List<ParticipantResultResponse> participants
) {

    public static SessionResultResponse pending() {
        return new SessionResultResponse(JudgeStatus.PENDING, null, null, null, null);
    }

    public static SessionResultResponse failed(FinishReason finishReason) {
        return new SessionResultResponse(JudgeStatus.FAILED, finishReason, null, null, null);
    }

    /** 기준별 요약을 기준 순서({@link JudgeCriterion}) 대로 늘어놓습니다. */
    public static List<CriterionResponse> criteria(Map<JudgeCriterion, String> summaries) {
        return Arrays.stream(JudgeCriterion.values())
                .map(criterion -> new CriterionResponse(criterion, summaries.get(criterion)))
                .toList();
    }

    public record CriterionResponse(JudgeCriterion key, String summary) {
    }

    /**
     * @param nickname        닉네임. AI 참가자와 계정이 정리된 참가자는 null
     * @param philosopherType 철학자 유형. 아직 users 에 없어 null (봇은 항상 null, 프론트가 봇 이미지를 씀)
     * @param isMe            조회한 사용자 본인인지
     * @param result          승패. 보관 시간이 지난 뒤 게스트는 DB 에 기록이 없어 null
     * @param scores          기준별 점수 (0 ~ 25). 보관 시간이 지났거나 몰수패면 null
     * @param total           총점 (0 ~ 100). {@code scores} 와 같이 null 일 수 있음
     */
    public record ParticipantResultResponse(
            Long participantId,
            String nickname,
            String philosopherType,
            Stance stance,
            boolean isMe,
            DebateResult result,
            Map<JudgeCriterion, Integer> scores,
            Integer total
    ) {
    }
}
