package likelion.yacha_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "auth.email-check.max-requests=3")
@DisplayName("이메일 중복 확인 호출 제한")
class EmailCheckLimiterTest {

    @Autowired
    private EmailCheckLimiter limiter;

    @BeforeEach
    void clear() {
        ((InMemoryEmailCheckLimiter) limiter).clear();
    }

    @Test
    @DisplayName("한 IP는 정해진 횟수까지만 허용하고 넘으면 false")
    void blocksAfterMaxRequests() {
        assertThat(limiter.tryAcquire("203.0.113.7")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.7")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.7")).isTrue();

        assertThat(limiter.tryAcquire("203.0.113.7")).isFalse();
    }

    @Test
    @DisplayName("IP마다 따로 센다")
    void countsPerIp() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("203.0.113.7");
        }

        assertThat(limiter.tryAcquire("203.0.113.7")).isFalse();
        assertThat(limiter.tryAcquire("198.51.100.2")).isTrue();
    }
}
