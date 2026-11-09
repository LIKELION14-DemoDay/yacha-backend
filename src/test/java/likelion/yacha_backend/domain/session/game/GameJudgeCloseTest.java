package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Game — 보관 시간이 끝나 판정 닫기")
class GameJudgeCloseTest {

    private static final int MAX_RUNS = 3;

    private static Game finishedGame() {
        Game game = new Game(1L, LocalDateTime.of(2026, 11, 6, 12, 0), Map.of(1L, 11L, 2L, 12L), null,
                100, 200, 200, 250);
        game.finish();
        return game;
    }

    @Test
    @DisplayName("판정이 실패한 뒤 닫으면 재판정을 시작하지 않는다")
    void closedGameDoesNotRejudge() {
        Game game = finishedGame();
        game.startJudging(MAX_RUNS);
        game.failJudging();

        assertThat(game.closeJudging()).isTrue();

        assertThat(game.startJudging(MAX_RUNS)).isFalse();
        assertThat(game.judgeStatus()).isEqualTo(JudgeStatus.FAILED);
    }

    @Test
    @DisplayName("재판정이 먼저 시작됐으면 닫지 않는다")
    void doesNotCloseWhileJudging() {
        Game game = finishedGame();
        game.startJudging(MAX_RUNS);
        game.failJudging();
        game.startJudging(MAX_RUNS);

        assertThat(game.closeJudging()).isFalse();

        // 그 재판정이 실패하면 다음 보관 시간 끝에는 닫힘
        game.failJudging();
        assertThat(game.closeJudging()).isTrue();
    }
}
