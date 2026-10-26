package likelion.yacha_backend.domain.session.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.judge.*} — 판정 (명세 2-6 · 2-9).
 *
 * <p>값이 빠지거나 범위를 벗어나면 판정이 돌지 않거나 결과가 바로 지워지므로 기동 단계에서 실패시킵니다.
 *
 * @param maxAttempts     판정 한 번의 최대 시도 횟수 (3). 시도당 타임아웃은 AI 구현의 HTTP 타임아웃(10초)입니다
 * @param retryBackoff    첫 재시도 전 대기 시간 (1초). 다음 재시도부터 두 배씩 늘립니다 (1초 → 2초)
 * @param maxRejudges     실패한 판정을 결과 조회로 다시 시작할 수 있는 횟수 (2). 다 쓰면 FAILED 를 그대로 줍니다.
 *                        0 은 재판정을 끄는 값이라, 값이 빠졌을 때 0 으로 바인딩되지 않게 {@code Integer} 로 받습니다
 * @param resultRetention 판정이 끝난 뒤 점수 · 요약을 메모리에 두는 시간 (10분). 지나면 승패만 남습니다
 */
@Validated
@ConfigurationProperties(prefix = "game.judge")
public record JudgeProperties(
        @Positive int maxAttempts,
        @NotNull Duration retryBackoff,
        @NotNull @PositiveOrZero Integer maxRejudges,
        @NotNull Duration resultRetention
) {

    /** 0 이하면 판정이 끝나자마자 게임째 지워져, 결과 화면이 점수 · 요약 없이 승패만 받습니다. */
    @AssertTrue(message = "game.judge.result-retention 은 0 보다 커야 합니다.")
    public boolean isResultRetentionPositive() {
        return resultRetention == null || (!resultRetention.isZero() && !resultRetention.isNegative());
    }

    /** 음수면 재시도 대기에서 예외가 나 재시도 없이 판정이 실패합니다. 0 은 바로 다시 시도합니다. */
    @AssertTrue(message = "game.judge.retry-backoff 은 0 이상이어야 합니다.")
    public boolean isRetryBackoffNotNegative() {
        return retryBackoff == null || !retryBackoff.isNegative();
    }
}
