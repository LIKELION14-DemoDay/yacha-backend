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

    private final PasswordResetProperties properties;

    @Override
    public void save(String token, Long userId) {
        // Redis 구현과 같게, 이전 토큰을 지워 사용자당 하나만 유효하게 함
        String previous = latestByUser.put(userId, token);
        if (previous != null) {
            tokens.remove(previous);
        }
        tokens.put(token, new Entry(userId, Instant.now().plus(properties.tokenTtl())));
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

    private record Entry(Long userId, Instant expiresAt) {
    }
}
