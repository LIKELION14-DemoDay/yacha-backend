package likelion.yacha_backend.domain.session.game;

/** 게임 메모리에 쌓이는 메시지 종류 (명세 2-4). */
public enum MessageType {

    /**
     * 공개된 주장 · 반론. 구간({@code phase})은 작성한 구간이라 {@code PREP} 이면 주장, {@code REBUTTAL} 이면 반론입니다.
     * 참가자별 채팅 수 상한에 세지 않습니다
     */
    ARGUMENT,
    /** {@code CHAT} 구간의 채팅 */
    CHAT
}
