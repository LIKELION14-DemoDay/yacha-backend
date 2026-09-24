package likelion.yacha_backend.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis 구현체. 운영 · 개발 · 로컬에서 사용합니다.
 *
 * <p>{@code @Profile("!test")} — 테스트에서는 인메모리 구현체가 대신 등록되므로 CI 에 Redis 가
 * 없어도 빌드가 돕니다.
 *
 * <p>저장 형태:
 * <pre>
 *   key   refresh:7
 *   value eyJhbGciOiJIUzI1NiJ9...
 *   TTL   14일 (jwt.refresh-token-validity)
 * </pre>
 *
 * <p>TTL 을 걸어 두면 만료된 토큰이 <b>알아서 사라집니다.</b> DB 였다면 만료 시각 컬럼을 두고
 * 조회할 때마다 확인하고, 쌓인 행을 지우는 작업까지 따로 만들어야 합니다.
 */
@Repository
@Profile("!test")
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public void save(Long userId, String token) {
        // set 은 값과 TTL 을 한 번에 설정합니다. 값만 덮어쓰면 기존 TTL 이 남아서
        // 새로 발급한 토큰이 예전 만료 시각에 사라지는 문제가 생깁니다.
        redisTemplate.opsForValue().set(key(userId), token, ttl());
    }

    @Override
    public Optional<String> find(Long userId) {
        // 만료된 키는 Redis 가 이미 지웠으므로 null 로 돌아옵니다. 만료 확인이 따로 필요 없습니다.
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private Duration ttl() {
        // 토큰 자체의 만료(exp)와 같은 값을 씁니다. 저장소가 더 오래 들고 있어 봐야
        // 검증 단계에서 어차피 만료로 걸러집니다.
        return Duration.ofMillis(jwtProperties.refreshTokenValidity());
    }
}
