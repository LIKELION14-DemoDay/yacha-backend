package likelion.yacha_backend.domain.session.game;

/** 게임 메모리에 쌓이는 메시지 종류 (명세 2-4). */
public enum MessageType {

    /** {@code CHAT_1} · {@code CHAT_2} 구간의 채팅 */
    CHAT,
    /** {@code FINAL} 구간의 최종변론. 참가자당 1건 */
    FINAL
}
