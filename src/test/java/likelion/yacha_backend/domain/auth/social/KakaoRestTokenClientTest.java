package likelion.yacha_backend.domain.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.Map;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 실제 카카오 대신 MockRestServiceServer 로 토큰 API 응답을 흉내 냄
 * 요청 형식과 응답 · 에러 해석만 확인 (id_token 검증은 SocialTokenVerifierTest)
 */
@DisplayName("카카오 인가 코드 교환")
class KakaoRestTokenClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/kakao";

    private MockRestServiceServer server;

    private KakaoRestTokenClient clientWith(KakaoCodeProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new KakaoRestTokenClient(properties, builder);
    }

    private KakaoRestTokenClient client() {
        return clientWith(new KakaoCodeProperties("rest-key", null, TOKEN_URI));
    }

    @Test
    @DisplayName("폼으로 코드를 보내고 응답의 id_token 을 돌려준다")
    void exchangesCode() {
        KakaoRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", "rest-key",
                        "redirect_uri", REDIRECT_URI,
                        "code", "auth-code")))
                .andRespond(withSuccess("""
                        {"access_token": "kakao-at", "token_type": "bearer", "id_token": "eyJ.id.token"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.exchangeForIdToken("auth-code", REDIRECT_URI)).isEqualTo("eyJ.id.token");
        server.verify();
    }

    @Test
    @DisplayName("Client Secret 이 설정돼 있으면 함께 보낸다")
    void sendsClientSecret() {
        KakaoRestTokenClient client = clientWith(new KakaoCodeProperties("rest-key", "secret", TOKEN_URI));
        server.expect(requestTo(TOKEN_URI))
                .andExpect(content().formDataContains(Map.of("client_secret", "secret")))
                .andRespond(withSuccess("""
                        {"id_token": "eyJ.id.token"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.exchangeForIdToken("auth-code", REDIRECT_URI)).isEqualTo("eyJ.id.token");
        server.verify();
    }

    @Test
    @DisplayName("코드가 만료 · 재사용되면(invalid_grant) INVALID_SOCIAL_CODE")
    void invalidGrant() {
        KakaoRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": "invalid_grant", "error_description": "authorization code not found", "error_code": "KOE320"}
                        """));

        assertThatThrownBy(() -> client.exchangeForIdToken("used-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_SOCIAL_CODE);
    }

    @Test
    @DisplayName("키가 틀리면(invalid_client) 사용자 탓이 아니므로 SOCIAL_PROVIDER_ERROR")
    void invalidClient() {
        KakaoRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": "invalid_client", "error_code": "KOE010"}
                        """));

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("카카오 5xx 면 SOCIAL_PROVIDER_ERROR")
    void serverError() {
        KakaoRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("타임아웃이면 SOCIAL_PROVIDER_ERROR")
    void timeout() {
        KakaoRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("응답에 id_token 이 없으면(OIDC 미설정) SOCIAL_PROVIDER_ERROR")
    void missingIdToken() {
        KakaoRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("""
                        {"access_token": "kakao-at", "token_type": "bearer"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("REST API 키가 없으면 카카오를 호출하지 않고 UNSUPPORTED_PROVIDER")
    void notConfigured() {
        KakaoRestTokenClient client = clientWith(new KakaoCodeProperties("", null, TOKEN_URI));

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.UNSUPPORTED_PROVIDER);
        server.verify();   // 요청이 하나도 나가지 않았어야 함
    }
}
