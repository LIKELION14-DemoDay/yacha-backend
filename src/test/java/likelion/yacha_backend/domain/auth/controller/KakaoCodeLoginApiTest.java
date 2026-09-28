package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.auth.social.KakaoTokenClient;
import likelion.yacha_backend.domain.auth.social.SocialProfile;
import likelion.yacha_backend.domain.auth.social.SocialTokenVerifier;
import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 카카오 없이 돌림. 코드 교환 · id_token 검증을 둘 다 가짜로 바꿔 끼워
 * 코드 → id_token → 로그인으로 이어지는지만 확인
 * (교환 자체는 KakaoRestTokenClientTest, 계정 처리 규칙은 SocialLoginApiTest)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(KakaoCodeLoginApiTest.StubConfig.class)
@DisplayName("카카오 인가 코드 로그인")
class KakaoCodeLoginApiTest {

    private static final String REDIRECT_URI = "http://localhost:5173/oauth/kakao";

    @TestConfiguration
    static class StubConfig {

        /** "code-xxx" → "idtoken-xxx". "used-" 로 시작하면 이미 쓴 코드로 취급 */
        @Bean
        @Primary
        KakaoTokenClient stubKakaoTokenClient() {
            return (code, redirectUri) -> {
                if (code.startsWith("used-") || !REDIRECT_URI.equals(redirectUri)) {
                    throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_CODE);
                }
                return "idtoken-" + code;
            };
        }

        /** id_token 을 그대로 sub 로 씀 */
        @Bean
        @Primary
        Map<Provider, SocialTokenVerifier> stubSocialTokenVerifiersForCode() {
            return Map.of(Provider.KAKAO, new SocialTokenVerifier() {

                @Override
                public Provider provider() {
                    return Provider.KAKAO;
                }

                @Override
                public SocialProfile verify(String idToken) {
                    return new SocialProfile(Provider.KAKAO, idToken, null, false, "카카오수민");
                }
            });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private String body(String code, String redirectUri) {
        return """
                {"code": "%s", "redirectUri": "%s"}
                """.formatted(code, redirectUri);
    }

    @Test
    @DisplayName("코드를 id_token 으로 바꿔 로그인하고 토큰 · 쿠키가 내려온다")
    void loginWithCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("code-1", REDIRECT_URI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.nickname").value("카카오수민"))
                .andExpect(cookie().exists("refreshToken"));

        User saved = userRepository.findByProviderAndProviderId(Provider.KAKAO, "idtoken-code-1").orElseThrow();
        assertThat(saved.getProvider()).isEqualTo(Provider.KAKAO);
    }

    @Test
    @DisplayName("이미 쓴 코드면 401 INVALID_SOCIAL_CODE")
    void usedCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("used-1", REDIRECT_URI)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_SOCIAL_CODE"));
    }

    @Test
    @DisplayName("code 가 비어 있으면 400")
    void blankCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" ", REDIRECT_URI)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("redirectUri 가 없으면 400")
    void missingRedirectUri() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "code-1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
