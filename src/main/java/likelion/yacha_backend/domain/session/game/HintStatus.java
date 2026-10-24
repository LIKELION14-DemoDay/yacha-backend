package likelion.yacha_backend.domain.session.game;

/** 힌트 생성 상태 (명세 2-5). {@code GET /sessions/{id}/hints} 의 {@code status} 와 같습니다. */
public enum HintStatus {

    /** 만드는 중. 프론트는 잠시 뒤 다시 조회합니다 */
    PENDING,
    /** 힌트 3개 있음 */
    READY,
    /** 만들지 못함. 다시 만들지 않고 힌트 없이 진행합니다 */
    FAILED,
}
