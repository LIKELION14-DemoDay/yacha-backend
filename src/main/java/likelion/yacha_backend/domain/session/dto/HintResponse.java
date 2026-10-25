package likelion.yacha_backend.domain.session.dto;

import java.util.List;
import likelion.yacha_backend.domain.session.game.GameHints;
import likelion.yacha_backend.domain.session.game.HintStatus;

/**
 * {@code GET /sessions/{id}/hints} — 내 힌트 (명세 2-5). 본인에게만 내려갑니다.
 *
 * @param status {@code PENDING} 만드는 중 / {@code READY} 있음 / {@code FAILED} 만들지 못함 (힌트 없이 진행)
 * @param hints  {@code READY} 일 때 3개, 그 밖에는 null
 */
public record HintResponse(HintStatus status, List<String> hints) {

    public static HintResponse from(GameHints hints) {
        return new HintResponse(hints.status(), hints.hints());
    }
}
