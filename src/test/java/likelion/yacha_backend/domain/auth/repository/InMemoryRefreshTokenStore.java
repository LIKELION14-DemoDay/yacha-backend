package likelion.yacha_backend.domain.auth.repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 테스트 전용 인메모리 구현체입니다.
 *
 * <p><b>src/test/java 에 있습니다.</b> 테스트 클래스도 컴포넌트 스캔 대상이라 빈으로 등록되지만,
 * 운영 jar 에는 포함되지 않습니다. 반대로 {@link RedisRefreshTokenStore} 는
 * {@code @Profile("!test")} 라 테스트에서는 뜨지 않습니다. 덕분에 <b>CI 에 Redis 가 없어도</b>
 * {@code ./gradlew clean build} 가 돕니다.
 *
 * <p>인터페이스를 둔 덕에 이런 교체가 가능합니다. 서비스 코드는 어느 구현체가 들어왔는지 모릅니다.
 */
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
        // Redis 의 TTL 을 흉내 냅니다. 만료된 값은 없는 것으로 봅니다.
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
