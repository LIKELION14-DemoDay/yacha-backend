package likelion.yacha_backend.domain.auth.repository;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 테스트 전용 인메모리 구현. Redis 구현처럼 처음 부른 때부터 구간을 셈 */
@Repository
@Profile("test")
@RequiredArgsConstructor
@EnableConfigurationProperties(EmailCheckProperties.class)
public class InMemoryEmailCheckLimiter implements EmailCheckLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final EmailCheckProperties properties;

    @Override
    public boolean tryAcquire(String clientIp) {
        Instant now = Instant.now();
        // compute는 키 하나에 대해 원자적이라 Lua 스크립트와 같은 동작
        Window window = windows.compute(clientIp, (ip, current) ->
                current == null || !current.endsAt().isAfter(now)
                        ? new Window(1, now.plus(properties.window()))
                        : new Window(current.count() + 1, current.endsAt()));
        return window.count() <= properties.maxRequests();
    }

    /** 테스트끼리 횟수가 섞이지 않게 비움 */
    public void clear() {
        windows.clear();
    }

    private record Window(int count, Instant endsAt) {
    }
}
