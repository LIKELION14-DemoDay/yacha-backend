package likelion.yacha_backend.domain.session.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import likelion.yacha_backend.domain.topic.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis 가 끊겨도 순환 위치 저장 · 조회 · 삭제가 예외를 던지지 않는지
 *
 * Redis 없이 확인함. 템플릿을 mock 으로 두고 연결 실패를 던지게 함
 */
@DisplayName("자동 제안 순환 위치 Redis — 연결 실패")
class RedisProposalCursorStoreFailureTest {

    private static final Long USER_ID = 1L;

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisProposalCursorStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        store = new RedisProposalCursorStore(redisTemplate);
    }

    @Test
    @DisplayName("저장이 실패해도 예외 없이 넘어간다")
    void saveFailureIsIgnored() {
        willThrow(new RedisConnectionFailureException("down"))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> store.save(USER_ID, new ProposalCursor(Category.values()[0], 1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("조회가 실패하면 위치가 없는 것으로 본다 (TTL 만료와 같음)")
    void findFailureReturnsEmpty() {
        given(valueOperations.get(anyString())).willThrow(new RedisConnectionFailureException("down"));

        assertThat(store.find(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("삭제가 실패해도 예외 없이 넘어간다")
    void deleteFailureIsIgnored() {
        given(redisTemplate.delete(anyString())).willThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> store.delete(USER_ID)).doesNotThrowAnyException();
    }
}
