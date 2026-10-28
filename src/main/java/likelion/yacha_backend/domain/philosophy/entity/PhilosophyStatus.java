package likelion.yacha_backend.domain.philosophy.entity;

/**
 * 1분 철학 공개 상태
 *
 * AI가 만든 글은 철학자가 하지 않은 말을 지어낼 수 있어서, 팀원이 검토 · 승인한 글만 사용자에게 보여줌
 */
public enum PhilosophyStatus {
    /** AI가 만든 뒤 검토를 기다리는 중. 사용자에게 보이지 않음 */
    PENDING,
    /** 승인됨. 그 주가 되면 사용자에게 보임 */
    APPROVED
}
