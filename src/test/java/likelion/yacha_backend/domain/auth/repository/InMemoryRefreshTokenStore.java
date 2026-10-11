package likelion.yacha_backend.domain.auth.repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 테스트 전용 인메모리 구현체 */
@Repository
@Profile("test")
@RequiredArgsConstructor
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final Map<Long, Entry> store = new ConcurrentHashMap<>();
    private final JwtProperties jwtProperties;

    @Override
    public void save(Long userId, String token) {
        Instant expiresAt = Instant.now().plusMillis(jwtProperties.refreshTokenValidity());
        store.put(userId, new Entry(token, expiresAt));
    }

    @Override
    public Optional<String> find(Long userId) {
        Entry entry = store.get(userId);
        if (entry == null) {
            return Optional.empty();
        }
        // Redis 의 TTL 을 흉내 냄
        if (entry.expiresAt().isBefore(Instant.now())) {
            store.remove(userId);
            return Optional.empty();
        }
        return Optional.of(entry.token());
    }

    /** ConcurrentHashMap의 computeIfPresent는 키 하나에 대해 원자적이라 Lua 스크립트와 같은 동작 */
    @Override
    public boolean replace(Long userId, String expected, String next) {
        AtomicBoolean replaced = new AtomicBoolean(false);
        store.computeIfPresent(userId, (id, entry) -> {
            if (entry.expiresAt().isBefore(Instant.now())) {
                return null;   // 만료된 값은 없는 것과 같음
            }
            if (!entry.token().equals(expected)) {
                return entry;
            }
            replaced.set(true);
            return new Entry(next, Instant.now().plusMillis(jwtProperties.refreshTokenValidity()));
        });
        return replaced.get();
    }

    @Override
    public void delete(Long userId) {
        store.remove(userId);
    }

    private record Entry(String token, Instant expiresAt) {
    }
}
