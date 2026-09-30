package likelion.yacha_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("비밀번호 재설정 저장소")
class PasswordResetStoreTest {

    @Autowired
    private PasswordResetStore store;

    @Test
    @DisplayName("테스트에서는 Redis 대신 인메모리 구현이 주입된다")
    void usesInMemoryInTests() {
        assertThat(store).isInstanceOf(InMemoryPasswordResetStore.class);
    }

    @Test
    @DisplayName("저장한 토큰으로 userId 를 꺼낸다")
    void saveAndConsume() {
        store.save("token-1", 7L);

        assertThat(store.consume("token-1")).contains(7L);
    }

    @Test
    @DisplayName("같은 토큰은 두 번 쓸 수 없다 (1회용)")
    void consumeOnlyOnce() {
        store.save("token-2", 7L);

        assertThat(store.consume("token-2")).contains(7L);
        assertThat(store.consume("token-2")).isEmpty();
    }

    @Test
    @DisplayName("없는 토큰은 빈 값이다")
    void unknownToken() {
        assertThat(store.consume("no-such-token")).isEmpty();
    }

    @Test
    @DisplayName("같은 이메일로 연속 요청하면 두 번째는 막힌다")
    void sendSlotIsLimited() {
        assertThat(store.tryAcquireSendSlot("a@example.com")).isTrue();
        assertThat(store.tryAcquireSendSlot("a@example.com")).isFalse();
    }

    @Test
    @DisplayName("다른 이메일은 서로 영향을 주지 않는다")
    void sendSlotIsPerEmail() {
        assertThat(store.tryAcquireSendSlot("b@example.com")).isTrue();
        assertThat(store.tryAcquireSendSlot("c@example.com")).isTrue();
    }

    @Test
    @DisplayName("같은 사용자에게 새 토큰을 발급하면 이전 토큰은 쓸 수 없다")
    void newTokenInvalidatesPrevious() {
        store.save("token-a", 101L);
        store.save("token-b", 101L);

        assertThat(store.consume("token-a")).isEmpty();
        assertThat(store.consume("token-b")).contains(101L);
    }

    @Test
    @DisplayName("새 토큰으로 재설정한 뒤에도 이전 토큰은 쓸 수 없다")
    void previousTokenStaysInvalidAfterReset() {
        store.save("token-c", 102L);
        store.save("token-d", 102L);

        assertThat(store.consume("token-d")).contains(102L);
        assertThat(store.consume("token-c")).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 토큰은 서로 무효화하지 않는다")
    void tokensArePerUser() {
        store.save("token-e", 103L);
        store.save("token-f", 104L);

        assertThat(store.consume("token-e")).contains(103L);
        assertThat(store.consume("token-f")).contains(104L);
    }
}
