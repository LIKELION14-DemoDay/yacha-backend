package likelion.yacha_backend.domain.session.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.hint.*} — 힌트 생성 (명세 2-5).
 *
 * <p>값이 빠지거나 범위를 벗어나면 힌트가 만들어지지 않으므로 기동 단계에서 실패시킵니다.
 *
 * @param maxAttempts  한 참가자의 최대 시도 횟수 (2). 시도당 시간은 AI 구현의 HTTP 타임아웃(10초)이 정합니다
 * @param retryBackoff 재시도 전 대기 시간 (1초). PREP 60초 안에 끝나도록 짧게 둡니다
 */
@Validated
@ConfigurationProperties(prefix = "game.hint")
public record HintProperties(
        @Positive int maxAttempts,
        @NotNull Duration retryBackoff
) {

    /** 음수면 재시도 대기에서 예외가 나 재시도 없이 실패합니다. 0 은 바로 다시 시도합니다. */
    @AssertTrue(message = "game.hint.retry-backoff 은 0 이상이어야 합니다.")
    public boolean isRetryBackoffNotNegative() {
        return retryBackoff == null || !retryBackoff.isNegative();
    }
}
