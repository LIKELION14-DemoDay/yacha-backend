package likelion.yacha_backend.domain.session.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.judge.*} — 판정 (명세 2-6 · 2-9).
 *
 * <p>값이 빠지면 판정이 돌지 않거나 결과가 지워지지 않으므로 기동 단계에서 실패시킵니다.
 *
 * @param maxAttempts     판정 한 번의 최대 시도 횟수 (3). 시도당 타임아웃은 AI 구현의 HTTP 타임아웃(10초)입니다
 * @param retryBackoff    첫 재시도 전 대기 시간 (1초). 다음 재시도부터 두 배씩 늘립니다 (1초 → 2초)
 * @param maxRejudges     실패한 판정을 결과 조회로 다시 시작할 수 있는 횟수 (2). 다 쓰면 FAILED 를 그대로 줍니다
 * @param resultRetention 판정이 끝난 뒤 점수 · 요약을 메모리에 두는 시간 (10분). 지나면 승패만 남습니다
 */
@Validated
@ConfigurationProperties(prefix = "game.judge")
public record JudgeProperties(
        @Positive int maxAttempts,
        @NotNull Duration retryBackoff,
        @PositiveOrZero int maxRejudges,
        @NotNull Duration resultRetention
) {
}
