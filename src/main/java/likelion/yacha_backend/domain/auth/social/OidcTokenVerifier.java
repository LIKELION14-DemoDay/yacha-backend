package likelion.yacha_backend.domain.auth.social;

import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * 카카오 · 구글이 공통으로 쓰는 OIDC {@code id_token} 검증 골격
 *
 * {@link JwtDecoder} 가 서명 · 발급자(iss) · 대상(aud) · 만료(exp)를 모두 확인
 * 공급자마다 다른 것은 claim 이름뿐이라 그 부분만 하위 클래스가 구현
 */
@Slf4j
@RequiredArgsConstructor
public abstract class OidcTokenVerifier implements SocialTokenVerifier {

    private final JwtDecoder jwtDecoder;

    @Override
    public SocialProfile verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException e) {
            // 서명 불일치 · 만료 · aud 불일치 등
            log.warn("{} id_token 검증 실패: {}", provider(), e.getMessage());
            throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            // sub 없이는 사용자를 식별할 수 없음
            // 정상적인 OIDC 토큰이면 항상 있음
            log.warn("{} id_token 에 sub 가 없습니다.", provider());
            throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        return toProfile(jwt, subject);
    }

    /** 공급자별 claim 이름에 맞춰 사용자 정보를 꺼냄 */
    protected abstract SocialProfile toProfile(Jwt jwt, String subject);

    /** claim이 없거나 타입이 다르면 {@code null}을 돌려줌 */
    protected String claimAsString(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    protected boolean claimAsBoolean(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        if (value instanceof Boolean b) {
            return b;
        }
        // 일부 공급자는 "true" 처럼 문자열로 보냄
        return value instanceof String s && Boolean.parseBoolean(s);
    }
}
