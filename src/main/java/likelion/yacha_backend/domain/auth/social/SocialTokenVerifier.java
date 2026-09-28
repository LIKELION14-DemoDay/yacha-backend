package likelion.yacha_backend.domain.auth.social;

import likelion.yacha_backend.domain.user.entity.Provider;

/**
 * 프론트가 보낸 소셜 {@code id_token} 을 검증해 사용자 정보를 꺼냄
 *
 * 서버가 카카오 · 구글 API 를 호출하지 않음
 * {@code id_token} 은 서명된 JWT라 공개키로 서명만 확인하면 됨
 * 공개키(JWKS)는 라이브러리가 캐시
 */
public interface SocialTokenVerifier {

    Provider provider();

    SocialProfile verify(String idToken);
}
