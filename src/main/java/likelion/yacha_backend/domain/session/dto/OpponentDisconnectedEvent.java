package likelion.yacha_backend.domain.session.dto;

import java.time.OffsetDateTime;

/**
 * 상대의 연결이 끊김 ({@code /topic/sessions/{id}}, 명세 2-4 · 2-11). 프론트는 "상대 연결 끊김" 을 띄웁니다.
 * {@code graceEndsAt} 까지 돌아오지 않으면 몰수패로 끝나고 {@code SESSION_FINISHED} 가 옵니다.
 *
 * @param type        항상 {@code OPPONENT_DISCONNECTED}
 * @param graceEndsAt 이탈 유예가 끝나는 시각
 */
public record OpponentDisconnectedEvent(String type, OffsetDateTime graceEndsAt) {

    public static OpponentDisconnectedEvent of(OffsetDateTime graceEndsAt) {
        return new OpponentDisconnectedEvent("OPPONENT_DISCONNECTED", graceEndsAt);
    }
}
