package likelion.yacha_backend.domain.session.repository;

import java.time.Duration;
import java.util.Optional;
import likelion.yacha_backend.domain.topic.entity.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis 구현체
 *
 * 저장 형태:
 *   key   proposal-cursor:{userId}
 *   value {시작 카테고리}:{다음 위치}  (예: TECH_AND_FUTURE:3)
 *   TTL   10분. 제안 화면을 오래 떠나 있으면 처음부터 다시 돕니다
 */
@Repository
@Profile("!test")
@RequiredArgsConstructor
public class RedisProposalCursorStore implements ProposalCursorStore {

    private static final String KEY_PREFIX = "proposal-cursor:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(Long userId, ProposalCursor cursor) {
        redisTemplate.opsForValue().set(key(userId), cursor.start().name() + ":" + cursor.nextOffset(), TTL);
    }

    @Override
    public Optional<ProposalCursor> find(Long userId) {
        String value = redisTemplate.opsForValue().get(key(userId));
        if (value == null) {
            return Optional.empty();
        }
        // 형식이 맞지 않는 값(배포 사이 형식 변경 등)은 없는 것으로 보고 처음부터 돕니다.
        try {
            int separator = value.lastIndexOf(':');
            return Optional.of(new ProposalCursor(Category.valueOf(value.substring(0, separator)),
                    Integer.parseInt(value.substring(separator + 1))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
