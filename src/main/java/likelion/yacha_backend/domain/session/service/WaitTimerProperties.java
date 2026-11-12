package likelion.yacha_backend.domain.session.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.wait.*} — 랜덤 방 대기 타이머 (명세 2-3-2).
 *
 * <p>값이 빠지면 대기 방이 정리되지 않으므로 {@code @NotNull} 로 기동 단계에서 실패시킵니다.
 *
 * @param firstPromptDelay 첫 {@code WAIT_PROMPT} 시각 (10초). 방을 만들고 이만큼 지나야 AI 전환을 받습니다
 * @param promptInterval   그 뒤 {@code WAIT_PROMPT} 간격 (30초). 방을 만든 시각 기준 30초 · 60초 … 에 보냅니다
 * @param limit          대기 상한. 지나면 방을 취소하고 {@code WAIT_EXPIRED} (5분)
 * @param sweepInterval  주기 정리 간격 (1분). 상한보다 이만큼 더 지났는데도 대기 중인 방을 취소합니다
 */
@Validated
@ConfigurationProperties(prefix = "game.wait")
public record WaitTimerProperties(
        @NotNull Duration firstPromptDelay,
        @NotNull Duration promptInterval,
        @NotNull Duration limit,
        @NotNull Duration sweepInterval
) {

    /**
     * 간격만큼 팝업을 미리 등록하므로 0 이하면 등록 반복이 끝나지 않고, 1초보다 짧으면 팝업이 지나치게 많이 잡힙니다.
     * 잘못된 설정으로 방 생성이 멈추지 않게 서버가 뜰 때 막습니다 (null 은 위의 @NotNull 이 검사).
     */
    @AssertTrue(message = "game.wait.prompt-interval 은 1초 이상이어야 합니다.")
    public boolean isPromptIntervalValid() {
        return promptInterval == null || promptInterval.compareTo(Duration.ofSeconds(1)) >= 0;
    }

    /** 첫 알림은 1초 이상, 간격 이하여야 합니다. 간격보다 늦으면 첫 알림이 두 번째 알림보다 늦게 나갑니다. */
    @AssertTrue(message = "game.wait.first-prompt-delay 는 1초 이상, prompt-interval 이하여야 합니다.")
    public boolean isFirstPromptDelayValid() {
        if (firstPromptDelay == null || promptInterval == null) {
            return true;
        }
        return firstPromptDelay.compareTo(Duration.ofSeconds(1)) >= 0 && firstPromptDelay.compareTo(promptInterval) <= 0;
    }

    /** 0 이하면 정리 작업이 쉬지 않고 돕니다. */
    @AssertTrue(message = "game.wait.sweep-interval 은 0 보다 커야 합니다.")
    public boolean isSweepIntervalPositive() {
        return sweepInterval == null || (!sweepInterval.isZero() && !sweepInterval.isNegative());
    }

    /** 0 이하면 방을 만들자마자 취소합니다. */
    @AssertTrue(message = "game.wait.limit 은 0 보다 커야 합니다.")
    public boolean isLimitPositive() {
        return limit == null || (!limit.isZero() && !limit.isNegative());
    }
}
