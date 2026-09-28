package likelion.yacha_backend.domain.session.entity;

/** 방 종류 (API 명세 2-3). */
public enum RoomType {

    /** 랜덤 야차. 자동 제안 · 방 찾기 · 관전 목록에 노출된다 */
    RANDOM,
    /** 친구와 야차. 초대 링크로만 들어온다 */
    FRIEND,
}
