package likelion.yacha_backend.domain.user.entity;

/** 밸런스 게임을 했는지. 홈에 들어올 때 게임을 띄울지 정합니다. */
public enum BalanceGameStatus {

    /** 아직 안 함. 도중에 나간 사람도 여기 — 홈에 들어오면 게임을 띄웁니다 */
    NOT_STARTED,
    /** 건너뜀 — 다시 띄우지 않고, 마이페이지에서만 시작합니다 */
    SKIPPED,
    /** 결과를 받음 — 마이페이지에서 다시 하면 덮어씁니다 */
    DONE,
}
