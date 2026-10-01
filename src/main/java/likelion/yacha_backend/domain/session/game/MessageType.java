package likelion.yacha_backend.domain.session.game;

/** 게임 메모리에 쌓이는 메시지 종류 (명세 2-4). */
public enum MessageType {

    /**
     * {@code PREP} · {@code REBUTTAL} 에 작성한 주장. 다음 채팅 구간이 시작될 때 공개되고, 구간은 그 채팅 구간입니다.
     * 참가자별 채팅 수 상한에 세지 않습니다
     */
    ARGUMENT,
    /** {@code CHAT_1} · {@code CHAT_2} 구간의 채팅 */
    CHAT,
    /** {@code FINAL} 구간의 최종변론. 참가자당 1건 */
    FINAL
}
