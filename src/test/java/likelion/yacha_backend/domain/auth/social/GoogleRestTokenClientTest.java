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
 * 실제 구글 대신 MockRestServiceServer 로 토큰 API 응답을 흉내 냄
 * 요청 형식과 응답 · 에러 해석만 확인 (id_token 검증은 SocialTokenVerifierTest)
 */
@DisplayName("구글 인가 코드 교환")
class GoogleRestTokenClientTest {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/google";

    private MockRestServiceServer server;

    private GoogleRestTokenClient clientWith(GoogleCodeProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GoogleRestTokenClient(properties, builder);
    }

    private GoogleRestTokenClient client() {
        return clientWith(new GoogleCodeProperties("web-client-id", "secret", TOKEN_URI));
    }

    @Test
    @DisplayName("폼으로 코드와 시크릿을 보내고 응답의 id_token 을 돌려준다")
    void exchangesCode() {
        GoogleRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", "web-client-id",
                        "client_secret", "secret",
                        "redirect_uri", REDIRECT_URI,
                        "code", "4/auth-code")))
                .andRespond(withSuccess("""
                        {"access_token": "google-at", "token_type": "Bearer", "id_token": "eyJ.id.token"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.exchangeForIdToken("4/auth-code", REDIRECT_URI)).isEqualTo("eyJ.id.token");
        server.verify();
    }

    @Test
    @DisplayName("코드가 만료 · 재사용되면(invalid_grant) INVALID_SOCIAL_CODE")
    void invalidGrant() {
        GoogleRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": "invalid_grant", "error_description": "Bad Request"}
                        """));

        assertThatThrownBy(() -> client.exchangeForIdToken("used-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_SOCIAL_CODE);
    }

    @Test
    @DisplayName("redirectUri 가 인가 요청 때와 다르면(redirect_uri_mismatch) INVALID_SOCIAL_CODE")
    void redirectUriMismatch() {
        GoogleRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": "redirect_uri_mismatch", "error_description": "Bad Request"}
                        """));

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", "http://localhost:5173/other"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_SOCIAL_CODE);
    }

    @Test
    @DisplayName("시크릿이 틀리면(invalid_client) 사용자 탓이 아니므로 SOCIAL_PROVIDER_ERROR")
    void invalidClient() {
        GoogleRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": "invalid_client", "error_description": "Unauthorized"}
                        """));

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("구글 5xx 면 SOCIAL_PROVIDER_ERROR")
    void serverError() {
        GoogleRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("타임아웃이면 SOCIAL_PROVIDER_ERROR")
    void timeout() {
        GoogleRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("응답에 id_token 이 없으면(scope 에 openid 누락) SOCIAL_PROVIDER_ERROR")
    void missingIdToken() {
        GoogleRestTokenClient client = client();
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("""
                        {"access_token": "google-at", "token_type": "Bearer"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("시크릿이 없으면 구글을 호출하지 않고 UNSUPPORTED_PROVIDER")
    void notConfigured() {
        GoogleRestTokenClient client = clientWith(new GoogleCodeProperties("web-client-id", "", TOKEN_URI));

        assertThatThrownBy(() -> client.exchangeForIdToken("auth-code", REDIRECT_URI))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.UNSUPPORTED_PROVIDER);
        server.verify();   // 요청이 하나도 나가지 않았어야 함
    }
}
