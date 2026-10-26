package likelion.yacha_backend.domain.philosophy.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml의 {@code philosophy.generation.*} — 1분 철학 글 생성 (#98)
 *
 * 생성 시각(cron)은 {@link PhilosophyGenerationJob}이 직접 읽음
 *
 * @param topicsPerWeek 한 주에 공개하는 글 편수 (5). 주제 하나가 한 편
 * @param maxAttempts   글 한 편의 최대 시도 횟수 (3). AI 호출 실패와 형식이 맞지 않는 응답 모두 한 번으로 셈
 * @param retryBackoff  첫 재시도 전 대기 시간 (2초). 다음 재시도부터 두 배씩 늘림
 */
@Validated
@ConfigurationProperties(prefix = "philosophy.generation")
public record PhilosophyGenerationProperties(
        @Positive int topicsPerWeek,
        @Positive int maxAttempts,
        @NotNull Duration retryBackoff
) {

    /** 음수면 재시도 대기에서 예외가 나 재시도 없이 실패함. 0은 바로 다시 시도 */
    @AssertTrue(message = "philosophy.generation.retry-backoff은 0 이상이어야 합니다.")
    public boolean isRetryBackoffNotNegative() {
        return retryBackoff == null || !retryBackoff.isNegative();
    }
}
