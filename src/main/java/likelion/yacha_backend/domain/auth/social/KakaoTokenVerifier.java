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
 *   이메일 확인 여부 claim({@code email_verified})도 함께 오지 않을 수 있음
 *   없으면 확인되지 않은 것으로 봄 — 기존 계정에 자동 연결하지 않음
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
