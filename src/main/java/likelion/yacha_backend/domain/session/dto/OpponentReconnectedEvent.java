package likelion.yacha_backend.domain.session.dto;

/**
 * 이탈 유예 안에 상대가 돌아옴 ({@code /topic/sessions/{id}}). 프론트는 "상대 연결 끊김" 표시를 내립니다.
 * 명세에 없던 이벤트로, 짧은 끊김 뒤 소켓이 다시 붙어 토론방을 다시 구독했을 때 보냅니다.
 *
 * @param type 항상 {@code OPPONENT_RECONNECTED}
 */
public record OpponentReconnectedEvent(String type) {

    public static OpponentReconnectedEvent of() {
        return new OpponentReconnectedEvent("OPPONENT_RECONNECTED");
    }
}
