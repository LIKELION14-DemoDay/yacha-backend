package likelion.yacha_backend.domain.auth.repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * Redis 구현
 * 두 값 모두 TTL 로 알아서 사라지므로 정리 작업이 필요 없음
 *
 *   password-reset:{토큰}        → userId   (30분)
 *   password-reset-user:{userId}  → 토큰     (30분)  사용자당 마지막 토큰
 *   password-reset-send:{이메일} → "1"      (1분)
 *   password-reset-code:{이메일} → { code, attempts }  (3분)  해시
 */
@Repository
@Profile("!test")
@RequiredArgsConstructor
public class RedisPasswordResetStore implements PasswordResetStore {

    private static final String TOKEN_PREFIX = "password-reset:";
    private static final String USER_PREFIX = "password-reset-user:";
    private static final String SEND_PREFIX = "password-reset-send:";
    private static final String CODE_PREFIX = "password-reset-code:";

    /**
     * 이전 인증번호를 지우고 새로 저장. 틀린 횟수도 0부터
     * KEYS[1] 키, ARGV[1] 인증번호, ARGV[2] 유효시간(밀리초)
     */
    private static final RedisScript<Long> SAVE_CODE = RedisScript.of("""
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'code', ARGV[1], 'attempts', 0)
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    /**
     * 확인 · 횟수 증가 · 삭제를 한 번에 실행
     * 0 = EXPIRED, 1 = MATCHED, 2 = MISMATCHED
     *
     * 횟수를 넘긴 요청은 맞는 번호여도 EXPIRED. 이때 지워서 다시 요청하게 함
     * 맞으면 바로 지워서 같은 번호로 두 번 통과하지 못하게 함
     * HINCRBY는 만료 시간을 바꾸지 않음
     *
     * KEYS[1] 키, ARGV[1] 입력한 인증번호, ARGV[2] 틀릴 수 있는 횟수
     */
    private static final RedisScript<Long> CHECK_CODE = RedisScript.of("""
            local code = redis.call('HGET', KEYS[1], 'code')
            if not code then
              return 0
            end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts > tonumber(ARGV[2]) then
              redis.call('DEL', KEYS[1])
              return 0
            end
            if code == ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 2
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final PasswordResetProperties properties;

    @Override
    public void save(String token, Long userId) {
        Duration ttl = properties.tokenTtl();
        // 새 토큰을 먼저 기록한 뒤 포인터를 바꿈
        // 순서가 반대면, 동시에 발급될 때 상대가 지우려 한 뒤에 기록돼 두 토큰이 함께 남을 수 있음
        redisTemplate.opsForValue()
                .set(TOKEN_PREFIX + token, String.valueOf(userId), ttl);

        // SET ... GET — 포인터를 새 토큰으로 바꾸면서 이전 토큰을 같은 동작으로 받아옴
        // 이전 토큰을 지워 사용자당 하나만 유효하게 함
        String previous = redisTemplate.opsForValue()
                .setGet(USER_PREFIX + userId, token, ttl);
        if (previous != null) {
            redisTemplate.delete(TOKEN_PREFIX + previous);
        }
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

    @Override
    public void saveCode(String email, String code) {
        redisTemplate.execute(SAVE_CODE, List.of(CODE_PREFIX + email),
                code, String.valueOf(properties.codeTtl().toMillis()));
    }

    @Override
    public CodeCheck checkCode(String email, String code) {
        Long result = redisTemplate.execute(CHECK_CODE, List.of(CODE_PREFIX + email),
                code, String.valueOf(properties.maxCodeAttempts()));
        if (result == null || result == 0L) {
            return CodeCheck.EXPIRED;
        }
        return result == 1L ? CodeCheck.MATCHED : CodeCheck.MISMATCHED;
    }
}
