package likelion.yacha_backend.domain.auth.social;

import likelion.yacha_backend.domain.user.entity.Provider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 구글 {@code id_token}
 *
 * 구글은 {@code email_verified}를 내려주므로,
 * 같은 이메일의 기존 계정에 자동으로 연결해도 안전한지 판단할 수 있음
 */
public class GoogleTokenVerifier extends OidcTokenVerifier {

    public GoogleTokenVerifier(JwtDecoder jwtDecoder) {
        super(jwtDecoder);
    }

    @Override
    public Provider provider() {
        return Provider.GOOGLE;
    }

    @Override
    protected SocialProfile toProfile(Jwt jwt, String subject) {
        return new SocialProfile(
                Provider.GOOGLE,
                subject,
                claimAsString(jwt, "email"),
                claimAsBoolean(jwt, "email_verified"),
                claimAsString(jwt, "name"));
    }
}
