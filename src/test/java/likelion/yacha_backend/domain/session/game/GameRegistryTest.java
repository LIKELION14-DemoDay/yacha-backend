package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("GameRegistry — 게임 메모리 저장소")
class GameRegistryTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 10, 31, 12, 0, 0);

    @Autowired
    private GameRegistry registry;

    @Autowired
    private GameProperties properties;

    @Test
    @DisplayName("application.yaml 의 game.* 이 바인딩된다 (채팅 300자 · 메시지 500건)")
    void bindsProperties() {
        assertThat(properties.chatMaxLength()).isEqualTo(300);
        assertThat(properties.maxMessages()).isEqualTo(500);
    }

    @Test
    @DisplayName("만든 게임을 세션 id 로 찾고, 지우면 사라진다")
    void createFindRemove() {
        Game game = registry.create(1001L, STARTED_AT, Map.of(1L, 11L, 2L, 12L));

        assertThat(registry.find(1001L)).containsSame(game);
        assertThat(game.getSessionId()).isEqualTo(1001L);
        assertThat(game.getStartedAt()).isEqualTo(STARTED_AT);

        registry.remove(1001L);

        assertThat(registry.find(1001L)).isEmpty();
    }

    @Test
    @DisplayName("설정값이 게임에 적용된다 (301자 채팅 거부)")
    void appliesProperties() {
        Game game = registry.create(1002L, STARTED_AT, Map.of(1L, 11L));
        try {
            game.appendChat(1L, "가".repeat(300), STARTED_AT.plusSeconds(60));
            assertThatThrownBy(() -> game.appendChat(1L, "가".repeat(301), STARTED_AT.plusSeconds(60)))
                    .hasMessage("글자 수를 초과했습니다.");
        } finally {
            registry.remove(1002L);
        }
    }

    @Test
    @DisplayName("같은 세션의 게임을 두 번 만들면 예외이고, 먼저 만든 게임은 그대로다")
    void duplicateCreate() {
        Game first = registry.create(1003L, STARTED_AT, Map.of(1L, 11L));
        try {
            assertThatThrownBy(() -> registry.create(1003L, STARTED_AT, Map.of(2L, 12L)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(registry.find(1003L)).containsSame(first);
        } finally {
            registry.remove(1003L);
        }
    }

    @Test
    @DisplayName("없는 게임은 비어 있다 (시작 전 · 정리됨 · 서버 재시작)")
    void findMissing() {
        assertThat(registry.find(404L)).isEmpty();
    }
}
