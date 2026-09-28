package likelion.yacha_backend.domain.auth.repository;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis 구현
 * 두 값 모두 TTL 로 알아서 사라지므로 정리 작업이 필요 없음
 *
 *   password-reset:{토큰}        → userId   (30분)
 *   password-reset-send:{이메일} → "1"      (1분)
 */
@Repository
@Profile("!test")
@RequiredArgsConstructor
public class RedisPasswordResetStore implements PasswordResetStore {

    private static final String TOKEN_PREFIX = "password-reset:";
    private static final String SEND_PREFIX = "password-reset-send:";

    private final StringRedisTemplate redisTemplate;
    private final PasswordResetProperties properties;

    @Override
    public void save(String token, Long userId) {
        redisTemplate.opsForValue()
                .set(TOKEN_PREFIX + token, String.valueOf(userId), properties.tokenTtl());
    }

    @Override
    public Optional<Long> consume(String token) {
        // GETDEL — 값을 읽으면서 같은 동작으로 삭제
        // 조회와 삭제를 나누면 같은 링크를 동시에 두 번 눌렀을 때 둘 다 통과
        String userId = redisTemplate.opsForValue().getAndDelete(TOKEN_PREFIX + token);
        return Optional.ofNullable(userId).map(Long::valueOf);
    }

    @Override
    public boolean tryAcquireSendSlot(String email) {
        // SET NX — 키가 없을 때만 넣고 true를 돌려줌
        // 이미 있으면(=최근에 보냈으면) false
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(SEND_PREFIX + email, "1", properties.sendInterval());
        return Boolean.TRUE.equals(acquired);
    }
}
