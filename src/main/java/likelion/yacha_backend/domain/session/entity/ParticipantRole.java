package likelion.yacha_backend.domain.session.entity;

/** 참가자 역할. */
public enum ParticipantRole {

    /** 방을 만든 사람(방장) */
    INITIATOR,
    /** 들어온 사람 또는 AI */
    OPPONENT,
}
