package likelion.yacha_backend.domain.session.dto;

import likelion.yacha_backend.domain.session.entity.DebatePhase;

/**
 * 주장 · 반론을 제출했다는 알림 ({@code /topic/sessions/{id}}, 명세 2-4). <b>내용은 넣지 않습니다</b> —
 * 공개 전까지 글은 본인만 봅니다. 상대 화면의 "상대방이 아직 작성중입니다…" 에 씁니다.
 *
 * @param type     항상 {@code ARGUMENT_SUBMITTED}
 * @param senderId 제출한 참가자 id
 * @param phase    작성 구간 ({@code PREP} 주장 / {@code REBUTTAL} 반론)
 */
public record ArgumentSubmittedEvent(
        String type,
        Long senderId,
        DebatePhase phase
) {

    public static ArgumentSubmittedEvent of(Long senderId, DebatePhase phase) {
        return new ArgumentSubmittedEvent("ARGUMENT_SUBMITTED", senderId, phase);
    }
}
