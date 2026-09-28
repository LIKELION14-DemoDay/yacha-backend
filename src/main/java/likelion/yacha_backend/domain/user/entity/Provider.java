package likelion.yacha_backend.domain.user.entity;

/**
 * 계정을 어디서 만들었는지
 * 사용자 식별은 {@code provider + provider_id} 조합으로
 */
public enum Provider {

    /** 이메일 · 비밀번호로 가입, 게스트도 여기에 속함 */
    LOCAL,
    KAKAO,
    GOOGLE,
}
