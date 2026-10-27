package likelion.yacha_backend.domain.ai;

import java.util.List;

/**
 * 준비된 채팅 대답 (명세 2-4). 봇 대답을 끝내 만들지 못했을 때와 임시 봇(stub)이 씁니다.
 *
 * <p>주제 · 입장과 무관하게 어색하지 않고, {@value BotWriter#CHAT_MAX_LENGTH}자 이내이며, 실패 티(사과 등)를 내지 않습니다.
 * 질문이 연달아 나오지 않게 순서를 정해 두었습니다.
 */
public final class PreparedReplies {

    public static final List<String> REPLIES = List.of(
            "근거를 조금 더 구체적으로 말해 줄 수 있나요?",
            "근거로 제시한 사례가 전체를 대표하기엔 부족해 보여요.",
            "그렇게 보면 반대 경우는 어떻게 설명하죠?",
            "일부만 맞다고 해서 전체 논리가 성립하는 건 아니죠.",
            "저는 여전히 제 입장이 더 설득력 있다고 봐요.",
            "그 말씀이 옳다면 왜 현실에선 다른 결과가 나올까요?",
            "특수한 상황에서만 성립하는 이야기일 수 있어요.",
            "모든 사람에게 똑같이 적용된다고 보긴 어려워요.",
            "그렇게 접근하면 논점이 본래 방향과 달라질 수 있어요.",
            "그 주장대로라면 생길 부작용도 따져 봐야 해요."
    );

    private PreparedReplies() {
    }

    /** 봇이 지금까지 보낸 채팅 수로 차례를 정해 돌려 씁니다. 상태를 따로 두지 않습니다. */
    public static String next(long botChatCount) {
        return REPLIES.get((int) (botChatCount % REPLIES.size()));
    }
}
