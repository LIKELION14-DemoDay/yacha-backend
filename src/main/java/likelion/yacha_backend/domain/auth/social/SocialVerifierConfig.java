package likelion.yacha_backend.domain.auth.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import likelion.yacha_backend.domain.auth.social.SocialProperties.Client;
import likelion.yacha_backend.domain.user.entity.Provider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
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
@EnableConfigurationProperties({SocialProperties.class, KakaoCodeProperties.class})
public class SocialVerifierConfig {

    /**
     * 설정된 공급자의 검증기만 만듦
     * 클라이언트 ID가 없는 공급자는 목록에 없고, 그 provider로 로그인 시도하면 {@code UNSUPPORTED_PROVIDER}가 됨
     * (로컬에서 카카오 키만 있어도 개발할 수 있게 하려는 것)
     */
    @Bean
    public Map<Provider, SocialTokenVerifier> socialTokenVerifiers(SocialProperties properties) {
        List<SocialTokenVerifier> verifiers = new ArrayList<>();

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

        Set<String> allowedIssuers = Set.copyOf(client.issuerUris());
        Set<String> allowedClientIds = Set.copyOf(client.clientIds());

        // exp · nbf. 기본 검증에 들어 있는 것과 같음
        OAuth2TokenValidator<Jwt> timestamps = new JwtTimestampValidator();

        // iss. 구글은 스킴이 있는 형태와 없는 형태를 모두 쓸 수 있어 목록으로 비교
        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<Object>(
                JwtClaimNames.ISS,
                iss -> iss != null && allowedIssuers.contains(iss.toString()));

        // aud. 기본 검증에 없어서 직접 넣음
        // 이게 없으면 다른 앱에서 발급된 구글 토큰으로도 우리 서비스에 로그인 가능
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.stream().anyMatch(allowedClientIds::contains));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, issuer, audience));
        return decoder;
    }
}
