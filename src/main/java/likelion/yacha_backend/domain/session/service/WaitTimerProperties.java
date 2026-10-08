package likelion.yacha_backend.domain.session.service;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.wait.*} — 랜덤 방 대기 타이머 (명세 2-3-2).
 *
 * <p>값이 빠지면 대기 방이 정리되지 않으므로 {@code @NotNull} 로 기동 단계에서 실패시킵니다.
 *
 * @param promptInterval {@code WAIT_PROMPT} 간격 (30초)
 * @param limit          대기 상한. 지나면 방을 취소하고 {@code WAIT_EXPIRED} (5분)
 */
@Validated
@ConfigurationProperties(prefix = "game.wait")
public record WaitTimerProperties(
        @NotNull Duration promptInterval,
        @NotNull Duration limit
) {
}
