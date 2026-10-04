package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    @DisplayName("application.yaml 의 game.* 이 바인딩된다 (채팅 100자 · 참가자당 채팅 200건 · 주장 200자 · 반론 250자)")
    void bindsProperties() {
        assertThat(properties.chatMaxLength()).isEqualTo(100);
        assertThat(properties.maxChatsPerParticipant()).isEqualTo(200);
        assertThat(properties.argumentMaxLength()).isEqualTo(200);
        assertThat(properties.rebuttalMaxLength()).isEqualTo(250);
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
    @DisplayName("설정값이 게임에 적용된다 (101자 채팅 · 201자 주장 · 251자 반론 거부)")
    void appliesProperties() {
        Game game = registry.create(1002L, STARTED_AT, Map.of(1L, 11L));
        try {
            game.saveMemo(1L, "가".repeat(200), STARTED_AT);
            assertThatThrownBy(() -> game.saveMemo(1L, "가".repeat(201), STARTED_AT))
                    .hasMessage("글자 수를 초과했습니다.");
            game.saveMemo(1L, "가".repeat(250), STARTED_AT.plusSeconds(80));
            assertThatThrownBy(() -> game.saveMemo(1L, "가".repeat(251), STARTED_AT.plusSeconds(80)))
                    .hasMessage("글자 수를 초과했습니다.");
            game.appendChat(1L, "가".repeat(100), STARTED_AT.plusSeconds(150));
            assertThatThrownBy(() -> game.appendChat(1L, "가".repeat(101), STARTED_AT.plusSeconds(150)))
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
    @DisplayName("지우기 전에 받아 둔 게임 참조로도 지운 뒤에는 채팅 · 주장을 기록할 수 없다")
    void removeBlocksHeldReference() {
        Game held = registry.create(1004L, STARTED_AT, Map.of(1L, 11L));

        registry.remove(1004L);

        assertThat(registry.find(1004L)).isEmpty();
        assertThat(held.isFinished()).isTrue();
        assertThatThrownBy(() -> held.appendChat(1L, "지운 뒤 채팅", STARTED_AT.plusSeconds(150)))
                .hasMessage("이미 종료되었거나 진행 중이 아닌 토론입니다.");
        assertThatThrownBy(() -> held.saveMemo(1L, "지운 뒤 주장", STARTED_AT))
                .hasMessage("이미 종료되었거나 진행 중이 아닌 토론입니다.");
        assertThat(held.messagesAfter(0)).isEmpty();
    }

    @Test
    @DisplayName("기록 중에 지우면 진행 중인 기록이 끝난 뒤 지워지고, 이후 기록은 거부된다")
    void removeWaitsForInFlightWrite() throws Exception {
        Game game = registry.create(1005L, STARTED_AT, Map.of(1L, 11L));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // sendChat 처럼 게임 락 안에서 기록 → 전송하는 도중이라고 가정한다
        Thread writer = new Thread(() -> {
            synchronized (game) {
                game.appendChat(1L, "락 안의 채팅", STARTED_AT.plusSeconds(150));
                locked.countDown();
                awaitQuietly(release);
            }
        });
        writer.start();
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        Thread remover = new Thread(() -> registry.remove(1005L));
        remover.start();
        // remove 는 finish() 에서 게임 락을 기다린다
        remover.join(200);
        assertThat(remover.isAlive()).isTrue();
        assertThat(game.isFinished()).isFalse();

        release.countDown();
        writer.join(5_000);
        remover.join(5_000);

        assertThat(registry.find(1005L)).isEmpty();
        assertThat(game.messagesAfter(0)).hasSize(1);
        assertThatThrownBy(() -> game.appendChat(1L, "지운 뒤 채팅", STARTED_AT.plusSeconds(151)))
                .hasMessage("이미 종료되었거나 진행 중이 아닌 토론입니다.");
    }

    @Test
    @DisplayName("없는 게임을 지워도 예외가 없다")
    void removeMissing() {
        registry.remove(405L);

        assertThat(registry.find(405L)).isEmpty();
    }

    @Test
    @DisplayName("없는 게임은 비어 있다 (시작 전 · 정리됨 · 서버 재시작)")
    void findMissing() {
        assertThat(registry.find(404L)).isEmpty();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
