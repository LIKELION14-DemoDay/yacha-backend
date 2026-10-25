package likelion.yacha_backend.domain.auth.repository;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;

/**
 * {@link IpRateLimiter}에 넘기는 한도 한 쌍. 설정 클래스 안에 넣어 쓰고, {@code @Valid}를 붙여야 검증됨
 *
 * @param maxRequests 한 IP가 {@code window} 안에 부를 수 있는 횟수
 * @param window      횟수를 세는 구간. 처음 부른 때부터 이만큼 지나면 0부터 다시 셈
 */
public record IpRateLimit(
        @Positive int maxRequests,
        @NotNull Duration window
) {

    /**
     * Redis에는 밀리초로 넘기므로(PEXPIRE) 1밀리초 이상이어야 함
     * 그보다 짧으면 Redis가 키를 바로 지워 제한이 꺼짐 (null은 위의 @NotNull이 검사)
     */
    @AssertTrue(message = "window는 1ms 이상이어야 합니다.")
    public boolean isWindowAtLeastOneMillisecond() {
        return window == null || window.toMillis() >= 1;
    }
}
