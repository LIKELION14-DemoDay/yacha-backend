package likelion.yacha_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IP별 호출 제한")
class IpRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final InMemoryIpRateLimiter limiter = new InMemoryIpRateLimiter();

    private boolean acquire(String purpose, String clientIp) {
        return limiter.tryAcquire(purpose, clientIp, 3, WINDOW);
    }

    @Test
    @DisplayName("한 IP는 정해진 횟수까지만 허용하고 넘으면 false")
    void blocksAfterMaxRequests() {
        assertThat(acquire("email-check", "203.0.113.7")).isTrue();
        assertThat(acquire("email-check", "203.0.113.7")).isTrue();
        assertThat(acquire("email-check", "203.0.113.7")).isTrue();

        assertThat(acquire("email-check", "203.0.113.7")).isFalse();
    }

    @Test
    @DisplayName("IP마다 따로 센다")
    void countsPerIp() {
        for (int i = 0; i < 3; i++) {
            acquire("email-check", "203.0.113.7");
        }

        assertThat(acquire("email-check", "203.0.113.7")).isFalse();
        assertThat(acquire("email-check", "198.51.100.2")).isTrue();
    }

    @Test
    @DisplayName("용도가 다르면 같은 IP여도 따로 센다")
    void countsPerPurpose() {
        for (int i = 0; i < 3; i++) {
            acquire("email-check", "203.0.113.7");
        }

        assertThat(acquire("email-check", "203.0.113.7")).isFalse();
        assertThat(acquire("password-reset-request", "203.0.113.7")).isTrue();
    }
}
