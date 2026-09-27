package likelion.yacha_backend.domain.user.entity;

/**
 * 계정을 어디서 만들었는지.
 *
 * <p>사용자 식별은 {@code provider + provider_id} 조합으로 합니다. 이메일은 바뀔 수 있어서
 * 식별자로 쓰면 안 됩니다. (구글 문서도 {@code sub} 만 고유 식별자로 쓰라고 안내합니다)
 */
public enum Provider {

    /** 이메일 · 비밀번호로 가입. 게스트도 여기에 속합니다. */
    LOCAL,
    KAKAO,
    GOOGLE,
}
