package likelion.yacha_backend.domain.session.game;

import java.util.List;
import java.util.Objects;

/**
 * 참가자 한 명의 힌트 (명세 2-5). 게임 메모리에만 두고 게임이 끝나면 버립니다.
 *
 * @param status 생성 상태
 * @param hints  {@code READY} 일 때만 3개, 그 밖에는 null
 */
public record GameHints(HintStatus status, List<String> hints) {

    static final GameHints PENDING = new GameHints(HintStatus.PENDING, null);
    static final GameHints FAILED = new GameHints(HintStatus.FAILED, null);

    public GameHints {
        Objects.requireNonNull(status, "status");
        hints = hints == null ? null : List.copyOf(hints);
    }

    static GameHints ready(List<String> hints) {
        return new GameHints(HintStatus.READY, Objects.requireNonNull(hints, "hints"));
    }
}
