package likelion.yacha_backend.domain.auth.social;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import likelion.yacha_backend.domain.auth.social.SocialProperties.Client;
import likelion.yacha_backend.domain.user.entity.Provider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 소셜 {@code id_token} 검증기 구성
 *
 * {@link NimbusJwtDecoder} 가 공개키(JWKS)를 받아 캐시하고 서명을 검증
 * 직접 만들면 키 회전 · 캐시 · 알고리즘 제한을 모두 다뤄야 하는데, 그 부분을 라이브러리에 맡김
 *
 * 검증 항목: 서명 · 만료(exp) · 발급자(iss) · 대상(aud)
 */
@Configuration
@EnableConfigurationProperties(SocialProperties.class)
public class SocialVerifierConfig {

    /**
     * 설정된 공급자의 검증기만 만듦
     * 클라이언트 ID가 없는 공급자는 목록에 없고, 그 provider로 로그인 시도하면 {@code UNSUPPORTED_PROVIDER}가 됨
     * (로컬에서 카카오 키만 있어도 개발할 수 있게 하려는 것)
     */
    @Bean
    public Map<Provider, SocialTokenVerifier> socialTokenVerifiers(SocialProperties properties) {
        List<SocialTokenVerifier> verifiers = new java.util.ArrayList<>();

        if (properties.google() != null && properties.google().isConfigured()) {
            verifiers.add(new GoogleTokenVerifier(decoder(properties.google())));
        }
        if (properties.kakao() != null && properties.kakao().isConfigured()) {
            verifiers.add(new KakaoTokenVerifier(decoder(properties.kakao())));
        }

        return verifiers.stream()
                .collect(Collectors.toUnmodifiableMap(SocialTokenVerifier::provider, Function.identity()));
    }

    private JwtDecoder decoder(Client client) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(client.jwkSetUri()).build();

        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(client.issuerUri());
        // aud는 기본 검증에 없어서 직접 넣음
        // 토큰의 aud가 우리 클라이언트 ID여야 함
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(client.clientId()));

        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                defaults, audience));
        return decoder;
    }
}
