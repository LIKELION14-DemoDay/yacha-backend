package likelion.yacha_backend.domain.session.entity;

/** 토론 세션 상태. */
public enum SessionStatus {

    /** 상대를 기다리는 중. 이 상태에서만 입장 · AI 전환 · 취소가 가능하다 */
    WAITING,
    /** 매칭 성사 후 진행 중 */
    IN_PROGRESS,
    /** 종료 */
    FINISHED,
    /** 시작하지 못하고 끝남 — 대기 취소 · 5분 상한 · 초대 만료 */
    CANCELLED,
}
