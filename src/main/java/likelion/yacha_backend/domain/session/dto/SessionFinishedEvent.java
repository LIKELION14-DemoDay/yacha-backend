package likelion.yacha_backend.domain.session.dto;

import likelion.yacha_backend.domain.session.entity.FinishReason;

/**
 * 토론이 끝남 ({@code /topic/sessions/{id}}, 명세 2-4). {@code COMPLETED} 면 프론트는 판정 화면으로 갑니다.
 *
 * @param type   항상 {@code SESSION_FINISHED}
 * @param reason 끝난 이유 ({@code COMPLETED} 시간 종료 · {@code FORFEIT} 몰수패)
 */
public record SessionFinishedEvent(String type, FinishReason reason) {

    public static SessionFinishedEvent of(FinishReason reason) {
        return new SessionFinishedEvent("SESSION_FINISHED", reason);
    }
}
