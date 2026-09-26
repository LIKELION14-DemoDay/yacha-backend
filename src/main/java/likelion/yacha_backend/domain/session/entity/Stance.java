package likelion.yacha_backend.domain.session.entity;

/** 주제(명제)에 대한 입장. */
public enum Stance {

    AGREE,
    DISAGREE,
    ;

    /** 상대에게 배정할 반대 입장. */
    public Stance opposite() {
        return this == AGREE ? DISAGREE : AGREE;
    }
}
