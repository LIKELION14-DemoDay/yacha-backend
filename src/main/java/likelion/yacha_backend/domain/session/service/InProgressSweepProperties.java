package likelion.yacha_backend.domain.session.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.in-progress.*} — 진행 중 게임의 주기 정리 · 끝난 게임의 메모리 정리.
 *
 * <p>값이 빠지면 끝나지 못한 게임 · 끝난 게임이 정리되지 않으므로 {@code @NotNull} 로 기동 단계에서 실패시킵니다.
 *
 * @param sweepInterval     주기 정리 간격 (1분). 시간표(260초)보다 이만큼 더 지났는데도 진행 중인 세션을 끝냅니다
 * @param finishedRetention 260초에 끝난 게임을 메모리에 두는 시간 (10분). 지나면 게임째 지웁니다
 */
@Validated
@ConfigurationProperties(prefix = "game.in-progress")
public record InProgressSweepProperties(
        @NotNull Duration sweepInterval,
        @NotNull Duration finishedRetention
) {

    /** 0 이하면 정리 작업이 쉬지 않고 돕니다. */
    @AssertTrue(message = "game.in-progress.sweep-interval 은 0 보다 커야 합니다.")
    public boolean isSweepIntervalPositive() {
        return sweepInterval == null || (!sweepInterval.isZero() && !sweepInterval.isNegative());
    }
}
