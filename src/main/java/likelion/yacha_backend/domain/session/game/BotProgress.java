package likelion.yacha_backend.domain.session.game;

import java.util.EnumMap;
import java.util.Map;
import likelion.yacha_backend.domain.session.entity.DebatePhase;

/**
 * 봇전에서 봇이 쓰는 글의 진행 상태. 게임과 함께 만들어지고 함께 버려집니다.
 *
 * <p><b>주장 · 반론</b> — AI 가 글을 다 쓴 때와 정한 제출 시각 중 <b>늦은 쪽</b>에 한 번 제출합니다.
 * {@link #prepared} 와 {@link #due} 중 나중에 불린 쪽이 제출할 글을 받습니다. 제출 시각에 글이 아직 없으면
 * 임시 글("생각중")을 먼저 내고, 글이 끝나면 그 글로 덮어씁니다. 작성 구간이 끝날 때까지 글이 없으면
 * {@link #fail} 로 실패 글을 내고, 그 뒤로는 봇이 아무것도 하지 않습니다 (게임은 무효로 끝남).
 *
 * <p><b>채팅 대답</b> — 사람이 말할 때마다 번호를 올리고, 대답 타이머는 자기 번호가 마지막일 때만 대답합니다
 * (마지막 말 뒤 한 번). 대답은 한 번에 하나만 만들고, 만드는 중에 차례가 오면 끝난 뒤 바로 한 번 더 만듭니다.
 * 실패해 다시 시도를 기다리는 동안도 만드는 중으로 봅니다.
 */
public class BotProgress {

    private final Map<DebatePhase, Draft> drafts = new EnumMap<>(Map.of(
            DebatePhase.PREP, new Draft(), DebatePhase.REBUTTAL, new Draft()));

    private long humanChatNo;
    private boolean replying;
    private boolean replyPending;
    private boolean failed;

    BotProgress() {
    }

    /** AI 가 글을 다 썼습니다. 제출 시각이 이미 지났으면 지금 제출할 글을, 아니면 null 을 돌려줍니다. */
    public synchronized Submission prepared(DebatePhase phase, String text) {
        Draft draft = drafts.get(phase);
        draft.text = text;
        return draft.takeText();
    }

    /**
     * 제출 시각이 됐습니다. 글이 이미 있으면 그 글을, 아직 없으면 {@code placeholder} 를 돌려줍니다.
     * 이미 냈거나 실패했으면 null 입니다.
     */
    public synchronized Submission due(DebatePhase phase, String placeholder) {
        Draft draft = drafts.get(phase);
        draft.due = true;
        Submission text = draft.takeText();
        if (text != null || draft.shown || failed) {
            return text;
        }
        draft.shown = true;
        return new Submission(placeholder, true);
    }

    /**
     * 작성 구간이 끝났습니다. 글을 내지 못했으면 실패로 표시하고 {@code failureText} 를 돌려줍니다.
     * 글을 이미 냈으면 null 입니다.
     */
    public synchronized Submission fail(DebatePhase phase, String failureText) {
        Draft draft = drafts.get(phase);
        if (draft.submitted || failed) {
            return null;
        }
        failed = true;
        boolean announce = !draft.shown;
        draft.shown = true;
        return new Submission(failureText, announce);
    }

    /**
     * 낸 글이 게임에 들어가지 못했습니다(글자 수 초과 등). 구간이 끝날 때 실패로 처리되게 되돌립니다.
     * 그 글이 {@code ARGUMENT_SUBMITTED} 를 맡았다면 알림도 나가지 않았으므로, 다음 글이 알림을 보내게 함께 되돌립니다.
     */
    public synchronized void rejected(DebatePhase phase, Submission submission) {
        Draft draft = drafts.get(phase);
        draft.submitted = false;
        if (submission.announce()) {
            draft.shown = false;
        }
    }

    /** 글을 쓰지 못해 게임을 무효로 끝내는 중인가. 그러면 봇은 더 쓰거나 대답하지 않습니다. */
    public synchronized boolean isFailed() {
        return failed;
    }

    /**
     * 사람이 채팅했습니다. 대답 타이머가 자기 차례인지 확인할 번호를 돌려줍니다.
     * 앞선 말 때문에 걸어 둔 "끝나면 한 번 더" 는 지웁니다 — 이 말의 타이머(3초 뒤)만 다음 대답을 정합니다.
     */
    public synchronized long humanSpoke() {
        replyPending = false;
        return ++humanChatNo;
    }

    /**
     * {@code chatNo} 번째 사람 말에 대답할 차례인가. 그 뒤로 사람이 또 말했으면 false 입니다(마지막 말에만 대답).
     * 다른 대답을 만드는 중이면 끝난 뒤 바로 대답하도록 표시하고 false 입니다.
     */
    public synchronized boolean startReply(long chatNo) {
        if (chatNo != humanChatNo) {
            return false;
        }
        return startOrQueue();
    }

    /** 지금까지 사람이 말한 횟수. 대답을 다시 시도하기 전에 그 사이 사람이 또 말했는지 볼 때 씁니다. */
    public synchronized long humanChatNo() {
        return humanChatNo;
    }

    /** 첫마디 — 사람이 아직 한 번도 말하지 않았고 만드는 대답이 없을 때만 true 입니다. */
    public synchronized boolean startFirstMessage() {
        if (humanChatNo > 0 || replying) {
            return false;
        }
        replying = true;
        return true;
    }

    /** 대답 하나를 끝냈습니다. 만드는 동안 차례가 왔으면 true — 바로 한 번 더 만듭니다. */
    public synchronized boolean finishReply() {
        if (replyPending) {
            replyPending = false;
            return true;
        }
        replying = false;
        return false;
    }

    private boolean startOrQueue() {
        if (replying) {
            replyPending = true;
            return false;
        }
        replying = true;
        return true;
    }

    /**
     * 게임에 넣을 봇 글.
     *
     * @param text   글
     * @param announce {@code ARGUMENT_SUBMITTED} 를 보내는가. 임시 글을 덮어쓸 때는 이미 보냈으므로 false
     */
    public record Submission(String text, boolean announce) {
    }

    private final class Draft {

        private String text;
        private boolean due;
        /** AI 가 쓴 글을 냈는가. */
        private boolean submitted;
        /** 무엇이든(임시 글 포함) 내서 ARGUMENT_SUBMITTED 를 보냈는가. */
        private boolean shown;

        private Submission takeText() {
            if (text == null || !due || submitted || failed) {
                return null;
            }
            submitted = true;
            boolean announce = !shown;
            shown = true;
            return new Submission(text, announce);
        }
    }
}
