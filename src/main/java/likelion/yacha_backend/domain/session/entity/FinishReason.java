package likelion.yacha_backend.domain.session.entity;

/** 종료 사유. */
public enum FinishReason {

    /** 시간표대로 끝남 */
    COMPLETED,
    /** 이탈로 몰수패 */
    FORFEIT,
    /** 서버 재시작 · 양쪽 동시 이탈 · 봇전에서 봇이 글을 끝내 못 씀 등으로 게임 무효. 승패를 남기지 않는다 */
    ABORTED,
}
