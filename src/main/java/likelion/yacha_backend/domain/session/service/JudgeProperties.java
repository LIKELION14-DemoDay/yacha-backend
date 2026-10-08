package likelion.yacha_backend.domain.session.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.judge.*} — 판정 (명세 2-6 · 2-9).
 *
 * <p>값이 빠지면 판정이 돌지 않거나 결과가 지워지지 않으므로 기동 단계에서 실패시킵니다.
 *
 * @param maxAttempts     판정 최대 시도 횟수 (3). 시도당 타임아웃은 AI 구현의 HTTP 타임아웃(10초)입니다
 * @param resultRetention 판정이 끝난 뒤 점수 · 요약을 메모리에 두는 시간 (10분). 지나면 승패만 남습니다
 */
@Validated
@ConfigurationProperties(prefix = "game.judge")
public record JudgeProperties(
        @Positive int maxAttempts,
        @NotNull Duration resultRetention
) {
}
