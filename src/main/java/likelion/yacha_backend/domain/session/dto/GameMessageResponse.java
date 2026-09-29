package likelion.yacha_backend.domain.session.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.game.GameMessage;
import likelion.yacha_backend.global.util.DateTimes;

/**
 * 토론방 메시지 한 건. {@code /topic/sessions/{id}} 이벤트와 {@code GET /sessions/{id}/messages} 가 같은 형식입니다.
 *
 * @param type       {@code CHAT} / {@code FINAL}
 * @param seqNo      게임 안의 순번. 서버가 1 부터 매깁니다
 * @param senderId   보낸 <b>참가자 id</b>. 사용자 id 가 아니라서 관전자에게 보여도 됩니다
 * @param phase      서버가 받은 시각의 구간
 * @param content    본문
 * @param receivedAt 서버가 받은 시각 (KST, {@code +09:00})
 */
public record GameMessageResponse(
        String type,
        long seqNo,
        Long senderId,
        DebatePhase phase,
        String content,
        OffsetDateTime receivedAt
) {

    public static GameMessageResponse from(GameMessage message, ZoneId zone) {
        return new GameMessageResponse(
                message.type().name(),
                message.seqNo(),
                message.participantId(),
                message.phase(),
                message.content(),
                DateTimes.withOffset(message.receivedAt(), zone));
    }

    /** 본문은 로그에 남기지 않습니다. */
    @Override
    public String toString() {
        return "GameMessageResponse[type=" + type + ", seqNo=" + seqNo + ", senderId=" + senderId
                + ", phase=" + phase + ", receivedAt=" + receivedAt + "]";
    }
}
