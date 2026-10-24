package likelion.yacha_backend.domain.session.game;

import java.util.EnumMap;
import java.util.Map;
import likelion.yacha_backend.domain.session.entity.DebatePhase;

/**
 * 봇전에서 봇이 쓰는 글의 진행 상태. 게임과 함께 만들어지고 함께 버려집니다.
 *
 * <p><b>주장 · 반론</b> — AI 가 글을 다 쓴 때와 정한 제출 시각 중 <b>늦은 쪽</b>에 한 번 제출합니다.
 * {@link #prepared} 와 {@link #due} 중 나중에 불린 쪽이 제출할 글을 받습니다.
 *
 * <p><b>채팅 대답</b> — 사람이 말할 때마다 번호를 올리고, 대답 타이머는 자기 번호가 마지막일 때만 대답합니다
 * (마지막 말 뒤 한 번). 대답은 한 번에 하나만 만들고, 만드는 중에 차례가 오면 끝난 뒤 바로 한 번 더 만듭니다.
 */
public class BotProgress {

    private final Map<DebatePhase, Draft> drafts = new EnumMap<>(Map.of(
            DebatePhase.PREP, new Draft(), DebatePhase.REBUTTAL, new Draft()));

    private long humanChatNo;
    private boolean replying;
    private boolean replyPending;

    BotProgress() {
    }

    /** AI 가 글을 다 썼습니다. 제출 시각이 이미 지났으면 지금 제출할 글을, 아니면 null 을 돌려줍니다. */
    public synchronized String prepared(DebatePhase phase, String text) {
        Draft draft = drafts.get(phase);
        draft.text = text;
        return draft.take();
    }

    /** 제출 시각이 됐습니다. 글이 이미 있으면 지금 제출할 글을, 아니면 null 을 돌려줍니다. */
    public synchronized String due(DebatePhase phase) {
        Draft draft = drafts.get(phase);
        draft.due = true;
        return draft.take();
    }

    /** 사람이 채팅했습니다. 대답 타이머가 자기 차례인지 확인할 번호를 돌려줍니다. */
    public synchronized long humanSpoke() {
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

    private static final class Draft {

        private String text;
        private boolean due;
        private boolean taken;

        private String take() {
            if (text == null || !due || taken) {
                return null;
            }
            taken = true;
            return text;
        }
    }
}
