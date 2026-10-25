package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Game — 힌트 상태")
class GameHintsTest {

    private static Game newGame() {
        return new Game(1L, LocalDateTime.of(2026, 10, 31, 12, 0), Map.of(1L, 11L, 2L, 12L), null,
                100, 200, 200, 250);
    }

    @Test
    @DisplayName("시작 전 · 만드는 중은 PENDING, 저장하면 READY, 참가자마다 따로 둔다")
    void perParticipant() {
        Game game = newGame();
        assertThat(game.hintsOf(1L).status()).isEqualTo(HintStatus.PENDING);

        game.startHints(1L);
        game.startHints(2L);
        game.completeHints(1L, List.of("a", "b", "c"));
        game.failHints(2L);

        assertThat(game.hintsOf(1L)).isEqualTo(new GameHints(HintStatus.READY, List.of("a", "b", "c")));
        assertThat(game.hintsOf(2L).status()).isEqualTo(HintStatus.FAILED);
        assertThat(game.hintsOf(2L).hints()).isNull();
    }

    @Test
    @DisplayName("게임이 끝난 뒤 도착한 결과는 버린다")
    void dropsAfterFinish() {
        Game game = newGame();
        game.startHints(1L);
        game.finish();

        game.completeHints(1L, List.of("a", "b", "c"));

        assertThat(game.hintsOf(1L).status()).isEqualTo(HintStatus.PENDING);
    }

    @Test
    @DisplayName("사람 참가자가 아니면 시작할 수 없다")
    void rejectsNonParticipant() {
        assertThatThrownBy(() -> newGame().startHints(99L)).isInstanceOf(IllegalArgumentException.class);
    }
}
