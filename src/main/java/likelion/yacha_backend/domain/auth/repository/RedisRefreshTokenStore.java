package likelion.yacha_backend.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis 구현체
 * 운영 · 개발 · 로컬에서 사용

 * 저장 형태:
 *   key   refresh:7
 *   value eyJhbGciOiJIUzI1NiJ9...
 *   TTL   14일 (jwt.refresh-token-validity)
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
        // set 은 값과 TTL 을 한 번에 설정
        redisTemplate.opsForValue().set(key(userId), token, ttl());
    }

    @Override
    public Optional<String> find(Long userId) {
        // 만료된 키는 Redis 가 이미 지웠으므로 null 로 돌아옴
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
        // 토큰 자체의 만료(exp)와 같은 값을 씀
        return Duration.ofMillis(jwtProperties.refreshTokenValidity());
    }
}
