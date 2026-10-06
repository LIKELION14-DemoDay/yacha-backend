package likelion.yacha_backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("리프레시 토큰 저장소")
class RefreshTokenStoreTest {

    @Autowired
    private RefreshTokenStore store;

    @Test
    @DisplayName("테스트에서는 Redis 대신 인메모리 구현체가 주입된다")
    void usesInMemoryImplementationInTests() {
        assertThat(store).isInstanceOf(InMemoryRefreshTokenStore.class);
    }

    @Test
    @DisplayName("저장한 토큰을 그대로 조회한다")
    void saveAndFind() {
        store.save(1L, "token-1");

        assertThat(store.find(1L)).contains("token-1");
    }

    @Test
    @DisplayName("같은 사용자를 다시 저장하면 이전 토큰을 덮어쓴다 (rotation)")
    void saveOverwrites() {
        store.save(2L, "old-token");
        store.save(2L, "new-token");

        assertThat(store.find(2L)).contains("new-token");
    }

    @Test
    @DisplayName("삭제하면 조회되지 않는다 (로그아웃)")
    void delete() {
        store.save(3L, "token-3");

        store.delete(3L);

        assertThat(store.find(3L)).isEmpty();
    }

    @Test
    @DisplayName("저장된 값이 기대한 값이면 새 값으로 바꾸고 true")
    void replaceWhenMatches() {
        store.save(4L, "old-token");

        assertThat(store.replace(4L, "old-token", "new-token")).isTrue();
        assertThat(store.find(4L)).contains("new-token");
    }

    @Test
    @DisplayName("저장된 값이 다르면 바꾸지 않고 false (그 사이 다른 요청이 저장함)")
    void replaceFailsWhenChanged() {
        store.save(5L, "someone-elses-token");

        assertThat(store.replace(5L, "old-token", "new-token")).isFalse();
        assertThat(store.find(5L)).contains("someone-elses-token");
    }

    @Test
    @DisplayName("저장된 값이 없으면 새로 만들지 않고 false (그 사이 로그아웃함)")
    void replaceFailsWhenDeleted() {
        assertThat(store.replace(6L, "old-token", "new-token")).isFalse();
        assertThat(store.find(6L)).isEmpty();
    }

    @Test
    @DisplayName("저장한 적 없는 사용자는 빈 값이다")
    void findReturnsEmptyWhenAbsent() {
        assertThat(store.find(999L)).isEmpty();
    }
}
