package likelion.yacha_backend.domain.session.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.disconnect.*} — 게임 중 연결 끊김 (명세 2-11).
 *
 * <p>값이 빠지거나 0 이하면 끊기자마자 몰수패가 되므로 기동 단계에서 실패시킵니다.
 *
 * @param grace 이탈 유예 (10초). 이 안에 토론방을 다시 구독하면 복귀하고, 지나면 몰수패입니다
 */
@Validated
@ConfigurationProperties(prefix = "game.disconnect")
public record DisconnectProperties(
        @NotNull Duration grace
) {

    /** 0 이하면 짧은 끊김도 바로 몰수패가 됩니다. */
    @AssertTrue(message = "game.disconnect.grace 는 0 보다 커야 합니다.")
    public boolean isGracePositive() {
        return grace == null || (!grace.isZero() && !grace.isNegative());
    }
}
