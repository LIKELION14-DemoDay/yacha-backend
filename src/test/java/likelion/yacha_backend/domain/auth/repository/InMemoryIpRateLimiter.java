package likelion.yacha_backend.domain.auth.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 테스트 전용 인메모리 구현. Redis 구현처럼 용도 · IP마다 처음 부른 때부터 구간을 셈 */
@Repository
@Profile("test")
public class InMemoryIpRateLimiter implements IpRateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String purpose, String clientIp, int maxRequests, Duration window) {
        Instant now = Instant.now();
        // compute는 키 하나에 대해 원자적이라 Lua 스크립트와 같은 동작
        Window counted = windows.compute(purpose + ":" + clientIp, (key, current) ->
                current == null || !current.endsAt().isAfter(now)
                        ? new Window(1, now.plus(window))
                        : new Window(current.count() + 1, current.endsAt()));
        return counted.count() <= maxRequests;
    }

    /** 테스트끼리 횟수가 섞이지 않게 비움 */
    public void clear() {
        windows.clear();
    }

    private record Window(int count, Instant endsAt) {
    }
}
