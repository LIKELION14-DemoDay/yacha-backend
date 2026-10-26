package likelion.yacha_backend.domain.session.repository;

import java.time.Duration;
import java.util.Optional;
import likelion.yacha_backend.domain.topic.entity.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis 구현체
 *
 * 저장 형태:
 *   key   proposal-cursor:{userId}
 *   value {시작 카테고리}:{다음 위치}  (예: TECH_AND_FUTURE:3)
 *   TTL   10분. 제안 화면을 오래 떠나 있으면 처음부터 다시 돕니다
 *
 * 순환 위치는 보조 상태라 Redis 가 실패해도 예외를 던지지 않고 로그만 남깁니다.
 * 위치를 잃으면 TTL 만료와 같이 처음부터 돌고, 제안 · 거절은 그대로 응답합니다.
 */
@Slf4j
@Repository
@Profile("!test")
@RequiredArgsConstructor
public class RedisProposalCursorStore implements ProposalCursorStore {

    private static final String KEY_PREFIX = "proposal-cursor:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(Long userId, ProposalCursor cursor) {
        try {
            redisTemplate.opsForValue().set(key(userId), cursor.start().name() + ":" + cursor.nextOffset(), TTL);
        } catch (DataAccessException e) {
            log.warn("[자동 제안] 순환 위치를 저장하지 못했습니다. userId={}", userId, e);
        }
    }

    @Override
    public Optional<ProposalCursor> find(Long userId) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(key(userId));
        } catch (DataAccessException e) {
            log.warn("[자동 제안] 순환 위치를 읽지 못했습니다. userId={}", userId, e);
            return Optional.empty();
        }
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
        try {
            redisTemplate.delete(key(userId));
        } catch (DataAccessException e) {
            log.warn("[자동 제안] 순환 위치를 지우지 못했습니다. TTL 로 사라집니다. userId={}", userId, e);
        }
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
