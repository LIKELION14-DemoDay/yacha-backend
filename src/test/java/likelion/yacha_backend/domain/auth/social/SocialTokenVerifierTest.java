package likelion.yacha_backend.domain.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 서명 · 발급자 · 만료 검증은 {@code NimbusJwtDecoder}가 하므로,
 * 여기서는 claim 해석과 실패 처리만 확인
 * 디코더를 가짜로 넣어 실제 카카오 · 구글 없이 돌림
 */
@DisplayName("소셜 id_token 검증")
class SocialTokenVerifierTest {

    /** 정해진 claim을 그대로 돌려주는 디코더. "bad"를 주면 검증 실패를 흉내 냄 */
    private static JwtDecoder decoderOf(Map<String, Object> claims) {
        return token -> {
            if ("bad".equals(token)) {
                throw new BadJwtException("서명이 올바르지 않습니다.");
            }
            Jwt.Builder builder = Jwt.withTokenValue(token).header("alg", "RS256");
            claims.forEach(builder::claim);
            return builder.build();
        };
    }

    @Nested
    @DisplayName("구글")
    class Google {

        @Test
        @DisplayName("sub · email · email_verified · name 을 읽는다")
        void readsClaims() {
            SocialTokenVerifier verifier = new GoogleTokenVerifier(decoderOf(Map.of(
                    "sub", "google-123",
                    "email", "soomin@example.com",
                    "email_verified", true,
                    "name", "수민")));

            SocialProfile profile = verifier.verify("token");

            assertThat(profile.provider()).isEqualTo(Provider.GOOGLE);
            assertThat(profile.providerId()).isEqualTo("google-123");
            assertThat(profile.email()).isEqualTo("soomin@example.com");
            assertThat(profile.nickname()).isEqualTo("수민");
            assertThat(profile.hasVerifiedEmail()).isTrue();
        }

        @Test
        @DisplayName("email_verified 가 false 면 기존 계정에 자동 연결하지 않는다")
        void unverifiedEmail() {
            SocialTokenVerifier verifier = new GoogleTokenVerifier(decoderOf(Map.of(
                    "sub", "google-123",
                    "email", "soomin@example.com",
                    "email_verified", false)));

            assertThat(verifier.verify("token").hasVerifiedEmail()).isFalse();
        }
    }

    @Nested
    @DisplayName("카카오")
    class Kakao {

        @Test
        @DisplayName("이메일 동의를 하지 않으면 이메일 없이 프로필을 만든다")
        void withoutEmail() {
            SocialTokenVerifier verifier = new KakaoTokenVerifier(decoderOf(Map.of(
                    "sub", "kakao-456",
                    "nickname", "야차37")));

            SocialProfile profile = verifier.verify("token");

            assertThat(profile.provider()).isEqualTo(Provider.KAKAO);
            assertThat(profile.providerId()).isEqualTo("kakao-456");
            assertThat(profile.email()).isNull();
            assertThat(profile.nickname()).isEqualTo("야차37");
            assertThat(profile.hasVerifiedEmail()).isFalse();
        }
    }

    @Nested
    @DisplayName("실패")
    class Failure {

        @Test
        @DisplayName("디코더가 거부하면 INVALID_SOCIAL_TOKEN")
        void invalidToken() {
            SocialTokenVerifier verifier = new GoogleTokenVerifier(decoderOf(Map.of("sub", "x")));

            assertThatThrownBy(() -> verifier.verify("bad"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        @Test
        @DisplayName("sub 가 없으면 사용자를 식별할 수 없어 실패한다")
        void missingSubject() {
            SocialTokenVerifier verifier = new GoogleTokenVerifier(decoderOf(Map.of("email", "a@b.com")));

            assertThatThrownBy(() -> verifier.verify("token"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("토큰이 비어 있으면 실패한다")
        void blankToken() {
            SocialTokenVerifier verifier = new GoogleTokenVerifier(decoderOf(Map.of("sub", "x")));

            assertThatThrownBy(() -> verifier.verify("  "))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
