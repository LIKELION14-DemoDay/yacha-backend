package likelion.yacha_backend.domain.session.game;

import java.time.LocalDateTime;
import likelion.yacha_backend.domain.session.entity.DebatePhase;

/**
 * 게임 메모리에 쌓인 메시지 한 건 (명세 1-5).
 *
 * <p>DB · Redis 에 저장하지 않고 게임이 끝나면 게임 객체와 함께 버립니다.
 * <b>{@code content} 를 로그에 찍지 마세요.</b> {@code toString()} 에서도 본문을 뺍니다.
 *
 * @param seqNo         게임 안의 순번. 1 부터 서버가 채번합니다
 * @param participantId 보낸 참가자 id. 사용자 id 는 노출하지 않습니다
 * @param type          채팅 / 공개된 주장 · 반론
 * @param phase         {@code CHAT} 은 서버가 받은 시각의 구간, {@code ARGUMENT} 는 작성 구간 ({@code PREP} 주장 / {@code REBUTTAL} 반론)
 * @param content       본문
 * @param receivedAt    서버가 받은 시각. {@code ARGUMENT} 는 공개한 시각
 */
public record GameMessage(
        long seqNo,
        Long participantId,
        MessageType type,
        DebatePhase phase,
        String content,
        LocalDateTime receivedAt
) {

    @Override
    public String toString() {
        return "GameMessage[seqNo=" + seqNo + ", participantId=" + participantId + ", type=" + type
                + ", phase=" + phase + ", length=" + Game.lengthOf(content) + ", receivedAt=" + receivedAt + "]";
    }
}
