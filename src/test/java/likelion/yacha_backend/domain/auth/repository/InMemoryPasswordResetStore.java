package likelion.yacha_backend.domain.auth.repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 테스트 전용 인메모리 구현. src/test 에 있어 운영 jar 에는 들어가지 않습니다.
 * (리프레시 토큰 저장소와 같은 방식 — CI 에 Redis 가 없어도 빌드가 돕니다)
 */
@Repository
@Profile("test")
@RequiredArgsConstructor
public class InMemoryPasswordResetStore implements PasswordResetStore {

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final Map<Long, String> latestByUser = new ConcurrentHashMap<>();
    private final Map<String, Instant> sendSlots = new ConcurrentHashMap<>();
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    private final Map<String, FailureCount> failures = new ConcurrentHashMap<>();

    private final PasswordResetProperties properties;

    @Override
    public void save(String token, Long userId) {
        // Redis 구현과 같은 순서 — 새 토큰을 먼저 넣고, 이전 토큰을 지워 사용자당 하나만 유효하게 함
        tokens.put(token, new Entry(userId, Instant.now().plus(properties.tokenTtl())));
        String previous = latestByUser.put(userId, token);
        if (previous != null) {
            tokens.remove(previous);
        }
    }

    @Override
    public Optional<Long> consume(String token) {
        // remove 로 꺼내면서 지웁니다. Redis 의 GETDEL 과 같은 동작입니다.
        Entry entry = tokens.remove(token);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    @Override
    public boolean tryAcquireSendSlot(String email) {
        Instant now = Instant.now();
        Instant until = sendSlots.get(email);
        if (until != null && until.isAfter(now)) {
            return false;
        }
        sendSlots.put(email, now.plus(properties.sendInterval()));
        return true;
    }

    @Override
    public void saveCode(String email, String code) {
        codes.put(email, new CodeEntry(code, 0, Instant.now().plus(properties.codeTtl())));
    }

    /**
     * 인증번호 · 하루 실패 횟수 두 맵을 함께 바꾸므로 메서드 전체를 잠가 Lua 스크립트처럼 한 번에 처리함
     * (테스트 전용이라 단순하게 둠)
     */
    @Override
    public synchronized CodeCheck checkCode(String email, String clientIp, String code) {
        Instant now = Instant.now();
        String ipKey = email + ":" + clientIp;
        if (failureCount(ipKey, now) >= properties.ipFailureLimit()
                || failureCount(email, now) >= properties.emailFailureLimit()) {
            return CodeCheck.LOCKED;
        }

        CodeEntry entry = codes.get(email);
        if (entry == null || entry.expiresAt().isBefore(now)) {
            codes.remove(email);
            return CodeCheck.EXPIRED;
        }
        int attempts = entry.attempts() + 1;
        if (attempts > properties.maxCodeAttempts()) {
            codes.remove(email);
            return CodeCheck.EXPIRED;
        }
        if (entry.code().equals(code)) {
            codes.remove(email);
            failures.remove(ipKey);
            failures.remove(email);
            return CodeCheck.MATCHED;
        }
        codes.put(email, new CodeEntry(entry.code(), attempts, entry.expiresAt()));
        addFailure(ipKey, now);
        addFailure(email, now);
        return CodeCheck.MISMATCHED;
    }

    /** 기간이 지난 횟수는 지우고 0으로 봄 (Redis의 TTL을 흉내 냄) */
    private int failureCount(String key, Instant now) {
        FailureCount failure = failures.get(key);
        if (failure == null) {
            return 0;
        }
        if (failure.resetsAt().isBefore(now)) {
            failures.remove(key);
            return 0;
        }
        return failure.count();
    }

    private void addFailure(String key, Instant now) {
        failures.merge(key, new FailureCount(1, now.plus(properties.failureWindow())),
                (current, first) -> new FailureCount(current.count() + 1, current.resetsAt()));
    }

    /** 재요청 제한(1분)을 기다리지 않고 다시 요청하는 테스트용 */
    public void clearSendSlots() {
        sendSlots.clear();
    }

    private record Entry(Long userId, Instant expiresAt) {
    }

    private record CodeEntry(String code, int attempts, Instant expiresAt) {
    }

    private record FailureCount(int count, Instant resetsAt) {
    }
}
