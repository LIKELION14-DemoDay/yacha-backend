package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Game — 연결 · 이탈 유예")
class GameConnectionTest {

    private static final Long HOST = 1L;
    private static final Long OPPONENT = 2L;

    private static Game newGame() {
        return new Game(1L, LocalDateTime.of(2026, 10, 31, 12, 0), Map.of(HOST, 11L, OPPONENT, 12L), null,
                100, 200, 200, 250);
    }

    @Test
    @DisplayName("인정한 연결이 끊기면 유예가 시작되고, 다시 구독하면 복귀한다")
    void disconnectAndReconnect() {
        Game game = newGame();
        assertThat(game.connect(HOST, "a")).isFalse();

        OptionalLong no = game.disconnect(HOST, "a");

        assertThat(no).isPresent();
        assertThat(game.isStillDisconnected(HOST, no.getAsLong())).isTrue();
        assertThat(game.connect(HOST, "b")).isTrue();
        assertThat(game.isStillDisconnected(HOST, no.getAsLong())).isFalse();
    }

    @Test
    @DisplayName("새 연결이 먼저 구독한 뒤 예전 연결이 끊기면 무시한다")
    void ignoresOldConnection() {
        Game game = newGame();
        game.connect(HOST, "old");
        game.connect(HOST, "new");

        assertThat(game.disconnect(HOST, "old")).isEmpty();
        assertThat(game.disconnect(HOST, "new")).isPresent();
    }

    @Test
    @DisplayName("다시 끊기면 새 번호를 받아, 앞선 끊김의 유예 타이머는 자기 끊김이 아니게 된다")
    void newNumberPerDisconnect() {
        Game game = newGame();
        game.connect(HOST, "a");
        long first = game.disconnect(HOST, "a").getAsLong();
        game.connect(HOST, "b");

        long second = game.disconnect(HOST, "b").getAsLong();

        assertThat(second).isNotEqualTo(first);
        assertThat(game.isStillDisconnected(HOST, first)).isFalse();
        assertThat(game.isStillDisconnected(HOST, second)).isTrue();
    }

    @Test
    @DisplayName("상대도 유예 중인지 알 수 있다 (양쪽 동시 이탈)")
    void otherDisconnected() {
        Game game = newGame();
        game.connect(HOST, "h");
        game.connect(OPPONENT, "o");
        game.disconnect(HOST, "h");

        assertThat(game.isOtherDisconnected(HOST)).isFalse();
        game.disconnect(OPPONENT, "o");
        assertThat(game.isOtherDisconnected(HOST)).isTrue();
    }

    @Test
    @DisplayName("참가자가 아니거나 끝난 게임이면 연결을 기록하지 않는다")
    void ignoresSpectatorAndFinished() {
        Game game = newGame();
        assertThat(game.connect(99L, "s")).isFalse();
        assertThat(game.disconnect(99L, "s")).isEmpty();

        game.connect(HOST, "a");
        game.finish();
        assertThat(game.disconnect(HOST, "a")).isEmpty();
    }
}
