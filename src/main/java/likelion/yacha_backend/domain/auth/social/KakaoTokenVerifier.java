package likelion.yacha_backend.domain.auth.social;

import likelion.yacha_backend.domain.user.entity.Provider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 카카오 {@code id_token}
 *
 *   이메일이 없을 수 있음
 *   이메일 제공이 선택 동의라, 동의하지 않으면 claim 자체가 내려오지 않음
 *   그 계정은 소셜로만 로그인할 수 있음
 *
 *   카카오 id_token 에는 {@code email_verified} claim 이 <b>오지 않습니다</b>
 *   (사용자 정보 API 에만 있습니다). 그래서 이메일이 와도 우리는 미확인으로 취급합니다 — 코드 리뷰 반영
 *   지금은 같은 이메일이어도 기존 계정에 연결하지 않으므로 동작 차이는 없습니다
 */
public class KakaoTokenVerifier extends OidcTokenVerifier {

    public KakaoTokenVerifier(JwtDecoder jwtDecoder) {
        super(jwtDecoder);
    }

    @Override
    public Provider provider() {
        return Provider.KAKAO;
    }

    @Override
    protected SocialProfile toProfile(Jwt jwt, String subject) {
        return new SocialProfile(
                Provider.KAKAO,
                subject,
                claimAsString(jwt, "email"),
                claimAsBoolean(jwt, "email_verified"),
                claimAsString(jwt, "nickname"));
    }
}
