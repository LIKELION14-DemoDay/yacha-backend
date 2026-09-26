package likelion.yacha_backend.domain.session.entity;

/** 종료 사유. */
public enum FinishReason {

    /** 시간표대로 끝남 */
    COMPLETED,
    /** 이탈로 몰수패 */
    FORFEIT,
    /** 서버 재시작 등으로 게임 무효. 채팅이 메모리에만 있어 이어갈 수 없다 */
    ABORTED,
}
