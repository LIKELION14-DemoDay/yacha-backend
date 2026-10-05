package likelion.yacha_backend.domain.session.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.game.GameMessage;
import likelion.yacha_backend.global.util.DateTimes;

/**
 * 토론방 메시지 한 건. {@code /topic/sessions/{id}} 이벤트와 {@code GET /sessions/{id}/messages} 가 같은 형식입니다.
 *
 * @param type       {@code ARGUMENT}(공개된 주장 · 반론) / {@code CHAT}
 * @param seqNo      게임 안의 순번. 서버가 1 부터 매깁니다
 * @param senderId   보낸 <b>참가자 id</b>. 사용자 id 는 노출하지 않습니다
 * @param phase      {@code CHAT} 은 서버가 받은 시각의 구간, {@code ARGUMENT} 는 작성 구간 ({@code PREP} 주장 / {@code REBUTTAL} 반론)
 * @param content    본문
 * @param receivedAt 서버가 받은 시각 (KST, {@code +09:00}). {@code ARGUMENT} 는 공개한 시각
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
