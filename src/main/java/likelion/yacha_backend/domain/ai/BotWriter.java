package likelion.yacha_backend.domain.ai;

import java.util.List;
import likelion.yacha_backend.domain.session.entity.Stance;

/**
 * 봇전의 봇이 쓰는 글 (명세 2-4). 봇도 사람과 같은 시간표 · 글자 수로 주장 · 반론을 쓰고, 채팅에 대답합니다.
 *
 * <p>실패하면 예외를 던지세요. 재시도와 실패 처리(빈 글 · 대답 건너뜀)는 부르는 쪽이 합니다.
 * 글자 수 상한을 넘으면 부르는 쪽이 자릅니다.
 */
public interface BotWriter {

    /** 봇 채팅 한 건의 글자 수 상한. 사람(100자)보다 짧습니다. */
    int CHAT_MAX_LENGTH = 50;

    /**
     * PREP 에 제출할 주장. 사람과 같은 상한({@code game.argument-max-length}, 200자)입니다.
     *
     * @param topic     주제
     * @param botStance 봇의 입장
     */
    String writeArgument(AiTopic topic, Stance botStance);

    /**
     * REBUTTAL 에 제출할 반론. 사람과 같은 상한({@code game.rebuttal-max-length}, 250자)입니다.
     *
     * @param topic            주제
     * @param botStance        봇의 입장
     * @param opponentArgument 공개된 상대 주장 원문. 상대가 내지 않았으면 빈 문자열
     */
    String writeRebuttal(AiTopic topic, Stance botStance, String opponentArgument);

    /**
     * 채팅 대답. 사람이 말할 때마다 3초 뒤에 부르고, 봇이 먼저 말을 걸지는 않습니다.
     *
     * @param topic            주제
     * @param botStance        봇의 입장
     * @param botParticipantId 봇의 참가자 id. 대화에서 봇이 한 말을 가려낼 때 씁니다
     * @param conversation     지금까지 공개된 주장 · 반론과 채팅 ({@code seqNo} 순서)
     * @return {@value #CHAT_MAX_LENGTH}자 이내 대답
     */
    String reply(AiTopic topic, Stance botStance, Long botParticipantId, List<AiMessage> conversation);
}
