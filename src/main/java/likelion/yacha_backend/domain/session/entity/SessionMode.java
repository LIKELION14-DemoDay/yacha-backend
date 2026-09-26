package likelion.yacha_backend.domain.session.entity;

/** 상대가 사람인지 AI 인지. */
public enum SessionMode {

    /** 사람 대 사람 */
    HUMAN,
    /** 봇전. 대기 중 AI 전환 · 자동 봇전으로 만들어진다 */
    AI,
}
