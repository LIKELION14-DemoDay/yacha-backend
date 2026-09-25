package likelion.yacha_backend.domain.auth.repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

    @Override
    public void delete(Long userId) {
        store.remove(userId);
    }

    private record Entry(String token, Instant expiresAt) {
    }
}
