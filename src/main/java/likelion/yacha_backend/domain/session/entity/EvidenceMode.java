package likelion.yacha_backend.domain.session.entity;

/** 근거 제공 여부. 유지할지는 결정 필요 (명세 PART 5). */
public enum EvidenceMode {

    /** 근거 없이 진행 */
    NONE,
    /** 매칭 직후 참가자별 근거 3건 제공 */
    ENABLED,
}
