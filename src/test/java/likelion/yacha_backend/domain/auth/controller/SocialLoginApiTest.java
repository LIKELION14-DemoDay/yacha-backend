package likelion.yacha_backend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import likelion.yacha_backend.domain.auth.social.SocialProfile;
import likelion.yacha_backend.domain.auth.social.SocialTokenVerifier;
import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 카카오 · 구글 없이 돌립니다. 검증기를 가짜로 바꿔 끼워 검증 이후의 계정 처리>만 봅니다.
 * (서명 검증 자체는 SocialTokenVerifierTest 에서 확인)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SocialLoginApiTest.StubVerifierConfig.class)
@DisplayName("소셜 로그인")
class SocialLoginApiTest {

    /** 토큰 문자열을 그대로 sub 로 쓰는 가짜 검증기. "bad-" 로 시작하면 검증 실패. */
    @TestConfiguration
    static class StubVerifierConfig {

        // 이름을 다르게 둡니다. 같은 이름이면 실제 설정의 빈 정의와 충돌합니다(override 금지가 기본).
        // 타입이 같은 빈이 둘이므로 @Primary 로 이쪽이 주입되게 합니다.
        @Bean
        @Primary
        Map<Provider, SocialTokenVerifier> stubSocialTokenVerifiers() {
            // KAKAO만 등록합니다. GOOGLE  "서버에 설정되지 않은 공급자"로 두고 테스트합니다.
            return Map.of(Provider.KAKAO, new SocialTokenVerifier() {

                @Override
                public Provider provider() {
                    return Provider.KAKAO;
                }

                @Override
                public SocialProfile verify(String idToken) {
                    if (idToken.startsWith("bad-")) {
                        throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
                    }
                    // 규칙: "sub|email|verified|nickname"
                    String[] parts = idToken.split("\\|", -1);
                    return new SocialProfile(
                            Provider.KAKAO,
                            parts[0],
                            parts[1].isBlank() ? null : parts[1],
                            Boolean.parseBoolean(parts[2]),
                            parts[3].isBlank() ? null : parts[3]);
                }
            });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private MvcResult login(String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "KAKAO", "idToken": "%s"}
                                """.formatted(idToken)))
                .andReturn();
    }

    private Integer userIdOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.userId");
    }

    @Test
    @DisplayName("처음 로그인하면 계정이 만들어지고 토큰 · 쿠키가 내려온다")
    void firstLoginCreatesAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "KAKAO", "idToken": "kakao-1|a@example.com|true|카카오수민"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.nickname").value("카카오수민"))
                .andExpect(jsonPath("$.data.isGuest").value(false))
                .andExpect(cookie().exists("refreshToken"));

        User saved = userRepository.findByEmail("a@example.com").orElseThrow();
        assertThat(saved.getProvider()).isEqualTo(Provider.KAKAO);
        assertThat(saved.getProviderId()).isEqualTo("kakao-1");
        assertThat(saved.getPassword()).isNull();
    }

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 계정이 새로 생기지 않는다")
    void secondLoginReusesAccount() throws Exception {
        Integer first = userIdOf(login("kakao-1|a@example.com|true|수민"));
        long countAfterFirst = userRepository.count();

        Integer second = userIdOf(login("kakao-1|a@example.com|true|수민"));

        assertThat(second).isEqualTo(first);
        assertThat(userRepository.count()).isEqualTo(countAfterFirst);
    }

    @Test
    @DisplayName("같은 이메일의 기존 계정이 있으면 연결하지 않고 409")
    void doesNotAutoLinkExistingAccount() throws Exception {
        // 우리 가입은 이메일 소유를 확인하지 않습니다. 자동으로 연결하면 남이 먼저 그 이메일로
        // 가입해 둔 계정에 진짜 주인이 들어가게 됩니다. (코드 리뷰 반영)
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "soomin@example.com", "password": "password123", "nickname": "수민"}
                                """))
                .andExpect(status().isOk());
        long before = userRepository.count();

        mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "KAKAO", "idToken": "kakao-9|Soomin@example.com|true|수민"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SOCIAL_EMAIL_CONFLICT"));

        // 계정이 만들어지지도, 기존 계정이 바뀌지도 않아야 합니다.
        assertThat(userRepository.count()).isEqualTo(before);
        User existing = userRepository.findByEmail("soomin@example.com").orElseThrow();
        assertThat(existing.getProvider()).isEqualTo(Provider.LOCAL);
        assertThat(existing.getProviderId()).isNull();
    }

    @Test
    @DisplayName("확인되지 않은 이메일이면 기존 계정에 연결하지 않고 새 계정을 만든다")
    void doesNotLinkUnverifiedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "victim@example.com", "password": "password123", "nickname": "피해자"}
                                """))
                .andExpect(status().isOk());
        Long victimId = userRepository.findByEmail("victim@example.com").orElseThrow().getId();

        // 남의 이메일을 적어 만든 소셜 계정. 확인되지 않았으므로 연결되면 안 된다.
        Integer socialId = userIdOf(login("kakao-evil|victim@example.com|false|공격자"));

        assertThat(socialId.longValue()).isNotEqualTo(victimId);
        // 확인되지 않은 이메일은 저장하지 않는다 (진짜 주인의 가입을 막지 않도록)
        assertThat(userRepository.findById(socialId.longValue()).orElseThrow().getEmail()).isNull();
    }

    @Test
    @DisplayName("카카오가 이메일 · 닉네임을 주지 않아도 가입된다")
    void createsAccountWithoutEmailAndNickname() throws Exception {
        MvcResult result = login("kakao-noemail|||");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        User saved = userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-noemail").orElseThrow();
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getNickname()).isNotBlank();   // 서버가 자동 생성
    }

    @Test
    @DisplayName("설정되지 않은 공급자면 400 UNSUPPORTED_PROVIDER")
    void unsupportedProvider() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "GOOGLE", "idToken": "google-1|a@example.com|true|수민"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_PROVIDER"));
    }

    @Test
    @DisplayName("검증에 실패하면 401 INVALID_SOCIAL_TOKEN")
    void invalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "KAKAO", "idToken": "bad-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_SOCIAL_TOKEN"));
    }

    @Test
    @DisplayName("id_token 이 비어 있으면 400")
    void blankToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider": "KAKAO", "idToken": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
