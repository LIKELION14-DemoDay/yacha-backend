package likelion.yacha_backend.domain.ai;

/** 판정 기준 (명세 2-6). 기준마다 0 ~ {@value #MAX_SCORE}점, 총점 0 ~ 100점입니다. */
public enum JudgeCriterion {

    /** 논리 */
    LOGIC,
    /** 근거 */
    EVIDENCE,
    /** 반박 */
    REBUTTAL,
    /** 일관성 */
    CONSISTENCY,
    ;

    /** 기준 하나의 만점 */
    public static final int MAX_SCORE = 25;
}
