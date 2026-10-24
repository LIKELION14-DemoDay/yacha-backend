package likelion.yacha_backend.domain.auth.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * Redis 구현체
 *
 * 저장 형태:
 *   key   email-check:203.0.113.7
 *   value 호출 횟수
 *   TTL   auth.email-check.window (처음 부른 때부터)
 */
@Repository
@Profile("!test")
@RequiredArgsConstructor
@EnableConfigurationProperties(EmailCheckProperties.class)
public class RedisEmailCheckLimiter implements EmailCheckLimiter {

    private static final String KEY_PREFIX = "email-check:";

    /**
     * INCR과 만료 설정을 한 번에 실행
     * 따로 하면 그 사이에 서버가 죽었을 때 만료 없는 키가 남아 그 IP가 영영 막힘
     *
     * KEYS[1] 키, ARGV[1] 구간(밀리초)
     */
    private static final RedisScript<Long> INCREMENT_IN_WINDOW = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final EmailCheckProperties properties;

    @Override
    public boolean tryAcquire(String clientIp) {
        Long count = redisTemplate.execute(
                INCREMENT_IN_WINDOW, List.of(KEY_PREFIX + clientIp),
                String.valueOf(properties.window().toMillis()));
        return count != null && count <= properties.maxRequests();
    }
}
