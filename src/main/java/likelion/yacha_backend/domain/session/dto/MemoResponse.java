package likelion.yacha_backend.domain.session.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.game.GameMemo;
import likelion.yacha_backend.global.util.DateTimes;

/**
 * 작성 중인 내 주장 ({@code PUT} · {@code GET /sessions/{id}/memo}). 본인에게만 내려갑니다.
 *
 * @param phase     작성한 구간 ({@code PREP} / {@code REBUTTAL})
 * @param content   본문. 비웠으면 빈 문자열
 * @param updatedAt 마지막으로 저장한 시각 (KST, {@code +09:00})
 */
public record MemoResponse(
        DebatePhase phase,
        String content,
        OffsetDateTime updatedAt
) {

    public static MemoResponse from(GameMemo memo, ZoneId zone) {
        return new MemoResponse(memo.phase(), memo.content(), DateTimes.withOffset(memo.updatedAt(), zone));
    }

    /** 본문은 로그에 남기지 않습니다. */
    @Override
    public String toString() {
        return "MemoResponse[phase=" + phase + ", length=" + (content == null ? 0 : content.length())
                + ", updatedAt=" + updatedAt + "]";
    }
}
