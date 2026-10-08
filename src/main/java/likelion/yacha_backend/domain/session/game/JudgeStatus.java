package likelion.yacha_backend.domain.session.game;

/** 판정 진행 상태 (명세 2-6 · 2-9). {@code GET /sessions/{id}/result} 의 {@code status} 와 같습니다. */
public enum JudgeStatus {

    /** 판정 중. 프론트는 1.5초마다 다시 조회합니다 */
    PENDING,
    /** 결과 있음 */
    READY,
    /** 판정 실패 · 결과 없음. 게임이 메모리에 남아 있으면 다시 조회할 때 판정을 다시 시작합니다 */
    FAILED,
}
