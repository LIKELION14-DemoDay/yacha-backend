package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.game.BotProgress.Submission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BotProgress — 봇 글 제출 시점 · 임시 글 · 실패 · 대답 차례")
class BotProgressTest {

    private static final String PLACEHOLDER = "생각중";
    private static final String FAILURE = "실패";

    @Test
    @DisplayName("글이 먼저 끝나면 제출 시각에 한 번만 제출한다")
    void submitsAtDueWhenReady() {
        BotProgress progress = new BotProgress();
        assertThat(progress.prepared(DebatePhase.PREP, "주장")).isNull();

        assertThat(progress.due(DebatePhase.PREP, PLACEHOLDER)).isEqualTo(new Submission("주장", true));
        assertThat(progress.due(DebatePhase.PREP, PLACEHOLDER)).isNull();
        assertThat(progress.fail(DebatePhase.PREP, FAILURE)).isNull();
    }

    @Test
    @DisplayName("제출 시각에 글이 없으면 임시 글을 내고, 글이 끝나면 알림 없이 덮어쓴다")
    void placeholderThenText() {
        BotProgress progress = new BotProgress();

        assertThat(progress.due(DebatePhase.REBUTTAL, PLACEHOLDER)).isEqualTo(new Submission(PLACEHOLDER, true));
        assertThat(progress.prepared(DebatePhase.REBUTTAL, "반론")).isEqualTo(new Submission("반론", false));
        assertThat(progress.fail(DebatePhase.REBUTTAL, FAILURE)).isNull();
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("작성 구간이 끝날 때까지 글이 없으면 실패 글을 내고, 그 뒤 끝난 글은 내지 않는다")
    void failsWithoutText() {
        BotProgress progress = new BotProgress();
        progress.due(DebatePhase.PREP, PLACEHOLDER);

        assertThat(progress.fail(DebatePhase.PREP, FAILURE)).isEqualTo(new Submission(FAILURE, false));
        assertThat(progress.isFailed()).isTrue();
        assertThat(progress.prepared(DebatePhase.PREP, "늦은 주장")).isNull();
        assertThat(progress.due(DebatePhase.REBUTTAL, PLACEHOLDER)).isNull();
    }

    @Test
    @DisplayName("임시 글을 내기 전에 실패하면 실패 글이 첫 제출이라 알림을 보낸다")
    void failBeforePlaceholderNotifies() {
        BotProgress progress = new BotProgress();

        assertThat(progress.fail(DebatePhase.PREP, FAILURE)).isEqualTo(new Submission(FAILURE, true));
    }

    @Test
    @DisplayName("낸 글이 게임에 들어가지 못하면(글자 수 초과 등) 구간이 끝날 때 실패로 처리된다")
    void rejectedTextFails() {
        BotProgress progress = new BotProgress();
        progress.due(DebatePhase.PREP, PLACEHOLDER);
        Submission text = progress.prepared(DebatePhase.PREP, "너무 긴 주장");

        progress.rejected(DebatePhase.PREP, text);

        // 임시 글이 이미 알림을 보냈으므로 실패 글은 알림 없이 덮어씁니다.
        assertThat(progress.fail(DebatePhase.PREP, FAILURE)).isEqualTo(new Submission(FAILURE, false));
    }

    @Test
    @DisplayName("알림을 맡은 글이 거절되면 알림도 나가지 않았으므로, 실패 글이 알림을 보낸다")
    void rejectedAnnouncingTextRestoresAnnounce() {
        BotProgress progress = new BotProgress();
        progress.prepared(DebatePhase.PREP, "너무 긴 주장");
        Submission text = progress.due(DebatePhase.PREP, PLACEHOLDER);
        assertThat(text).isEqualTo(new Submission("너무 긴 주장", true));

        progress.rejected(DebatePhase.PREP, text);

        assertThat(progress.fail(DebatePhase.PREP, FAILURE)).isEqualTo(new Submission(FAILURE, true));
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
