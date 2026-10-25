package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.session.entity.DebatePhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BotProgress — 봇 글 제출 시점 · 대답 차례")
class BotProgressTest {

    @Test
    @DisplayName("글이 먼저 끝나면 제출 시각에, 제출 시각이 먼저 오면 글이 끝날 때 한 번만 제출한다")
    void submitsAtLaterOfReadyAndDue() {
        BotProgress early = new BotProgress();
        assertThat(early.prepared(DebatePhase.PREP, "주장")).isNull();
        assertThat(early.due(DebatePhase.PREP)).isEqualTo("주장");
        assertThat(early.due(DebatePhase.PREP)).isNull();

        BotProgress late = new BotProgress();
        assertThat(late.due(DebatePhase.REBUTTAL)).isNull();
        assertThat(late.prepared(DebatePhase.REBUTTAL, "반론")).isEqualTo("반론");
    }

    @Test
    @DisplayName("마지막 사람 말에만 대답하고, 만드는 중에 차례가 오면 끝난 뒤 한 번 더 만든다")
    void replyTurns() {
        BotProgress progress = new BotProgress();
        long first = progress.humanSpoke();
        long second = progress.humanSpoke();

        assertThat(progress.startReply(first)).isFalse();
        assertThat(progress.startReply(second)).isTrue();
        long third = progress.humanSpoke();
        assertThat(progress.startReply(third)).isFalse();
        assertThat(progress.finishReply()).isTrue();
        assertThat(progress.finishReply()).isFalse();
    }

    @Test
    @DisplayName("만드는 중에 걸린 '한 번 더' 는 사람이 또 말하면 지워져, 그 말의 차례가 와야 다음 대답을 만든다")
    void newMessageClearsPendingReply() {
        BotProgress progress = new BotProgress();
        assertThat(progress.startReply(progress.humanSpoke())).isTrue();
        assertThat(progress.startReply(progress.humanSpoke())).isFalse();

        long third = progress.humanSpoke();

        assertThat(progress.finishReply()).isFalse();
        assertThat(progress.startReply(third)).isTrue();
    }

    @Test
    @DisplayName("첫마디는 사람이 한 번도 말하지 않았을 때만")
    void firstMessageOnlyWhenSilent() {
        BotProgress silent = new BotProgress();
        assertThat(silent.startFirstMessage()).isTrue();

        BotProgress spoken = new BotProgress();
        spoken.humanSpoke();
        assertThat(spoken.startFirstMessage()).isFalse();
    }
}
