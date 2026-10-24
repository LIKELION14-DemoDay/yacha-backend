package likelion.yacha_backend.domain.session.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.bot.*} — 봇전의 봇 발언 (명세 2-4).
 *
 * <p>값이 빠지거나 범위를 벗어나면 봇이 말하지 않거나 끝없이 말하므로 기동 단계에서 실패시킵니다.
 *
 * @param maxReplies        한 게임에서 봇이 보내는 채팅 수 상한 (30, 첫마디 포함). 넘으면 대답하지 않습니다
 * @param replyDelay        사람의 마지막 말 뒤 대답까지 (3초)
 * @param firstMessageDelay 채팅이 열린 뒤 사람이 조용하면 봇이 먼저 말하기까지 (10초)
 * @param maxAttempts       주장 · 반론의 최대 시도 횟수 (2). 채팅 대답은 다시 시도하지 않습니다
 * @param retryBackoff      주장 · 반론 재시도 전 대기 시간 (1초)
 */
@Validated
@ConfigurationProperties(prefix = "game.bot")
public record BotProperties(
        @Positive int maxReplies,
        @NotNull Duration replyDelay,
        @NotNull Duration firstMessageDelay,
        @Positive int maxAttempts,
        @NotNull Duration retryBackoff
) {

    /** 음수 시간은 의미가 없고, 재시도 대기에서는 예외가 납니다. 0 은 바로 실행합니다. */
    @AssertTrue(message = "game.bot 의 시간 값은 0 이상이어야 합니다.")
    public boolean isDurationsNotNegative() {
        return notNegative(replyDelay) && notNegative(firstMessageDelay) && notNegative(retryBackoff);
    }

    private static boolean notNegative(Duration duration) {
        return duration == null || !duration.isNegative();
    }
}
