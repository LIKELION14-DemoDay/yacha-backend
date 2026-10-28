package likelion.yacha_backend.domain.balancegame.service;

import java.util.Objects;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;

/**
 * 로그인 전 온보딩에서 받은 밸런스 게임 결과
 * 저장하지 않고 쿠키로 들고 있다가, 로그인할 때 계정에 저장함
 *
 * @param type 결과 유형. 건너뛰었으면 null
 */
public record PreviewResult(PhilosopherType type) {

    public static PreviewResult of(PhilosopherType type) {
        return new PreviewResult(Objects.requireNonNull(type, "type"));
    }

    public static PreviewResult skipped() {
        return new PreviewResult(null);
    }

    public boolean isSkipped() {
        return type == null;
    }
}
