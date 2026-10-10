package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Game — 판정 상태")
class GameJudgingTest {

    private static Game newGame() {
        return new Game(1L, LocalDateTime.of(2026, 10, 31, 12, 0), Map.of(1L, 11L, 2L, 12L), null,
                100, 200, 200, 250);
    }

    @Test
    @DisplayName("끝나지 않은 게임은 판정할 수 없다")
    void notFinished() {
        assertThatThrownBy(newGame()::startJudging).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("판정은 한 번만 시작하고, 판정 중 · 완료면 다시 시작하지 않는다")
    void startsOnce() {
        Game game = newGame();
        game.finish();

        assertThat(game.judgeStatus()).isNull();
        assertThat(game.startJudging()).isTrue();
        assertThat(game.judgeStatus()).isEqualTo(JudgeStatus.PENDING);
        assertThat(game.startJudging()).isFalse();

        Verdict verdict = Verdict.of(List.of(11L, 12L), VerdictTest.result(11L, 20, 12L, 10));
        game.completeJudging(verdict);
        assertThat(game.judgeStatus()).isEqualTo(JudgeStatus.READY);
        assertThat(game.verdict()).isSameAs(verdict);
        assertThat(game.startJudging()).isFalse();
    }

    @Test
    @DisplayName("실패한 판정은 다시 시작할 수 있다")
    void restartsAfterFailure() {
        Game game = newGame();
        game.finish();
        game.startJudging();

        game.failJudging();

        assertThat(game.judgeStatus()).isEqualTo(JudgeStatus.FAILED);
        assertThat(game.startJudging()).isTrue();
    }
}
