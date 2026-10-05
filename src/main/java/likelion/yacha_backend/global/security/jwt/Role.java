package likelion.yacha_backend.global.security.jwt;

public enum Role {
    /**
     * 비회원. 관전 · 게임만 할 수 있음
     * DB에는 저장하지 않고 토큰에만 실림 (User#tokenRole)
     */
    GUEST,
    USER,
    ADMIN,
}
