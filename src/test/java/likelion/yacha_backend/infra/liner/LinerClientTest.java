package likelion.yacha_backend.infra.liner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("LINER Web Search Client")
class LinerClientTest {

    private static final String BASE_URL = "https://platform.liner.com";

    private MockRestServiceServer server;

    private LinerClient clientWith(LinerProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);

        server = MockRestServiceServer.bindTo(builder).build();

        return new LinerClient(properties, builder);
    }

    private LinerClient client() {
        return clientWith(
                new LinerProperties(BASE_URL, "test-api-key")
        );
    }

    @Test
    @DisplayName("LINER 응답 본문이 비어 있으면 LINER_PROVIDER_ERROR")
    void emptyResponse() {
        LinerClient client = client();

        server.expect(
                        requestTo(BASE_URL + "/api/v1/tools/search/web")
                )
                .andRespond(
                        withSuccess()
                );

        assertThatThrownBy(
                () -> client.searchWeb("테스트", 3)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LinerErrorCode.LINER_PROVIDER_ERROR);

        server.verify();
    }

    @Test
    @DisplayName("검색어와 API Key를 보내고 검색 결과를 반환한다")
    void searchWeb() {
        LinerClient client = client();

        server.expect(
                        requestTo(BASE_URL + "/api/v1/tools/search/web")
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        header("x-api-key", "test-api-key")
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        content().json("""
                                {
                                  "query": "친구의 범죄를 신고해야 하는 근거",
                                  "country_code": "kr",
                                  "lang": "ko",
                                  "max_results": 3
                                }
                                """)
                )
                .andRespond(
                        withSuccess("""
                                {
                                  "requestId": "request-123",
                                  "results": [
                                    {
                                      "title": "테스트 기사",
                                      "url": "https://example.com/article",
                                      "hostname": "example.com",
                                      "faviconUrl": "https://example.com/favicon.ico",
                                      "description": "테스트 검색 결과입니다.",
                                      "date": "2026-09-30"
                                    }
                                  ],
                                  "totalCount": 1
                                }
                                """, MediaType.APPLICATION_JSON)
                );

        LinerSearchResponse response =
                client.searchWeb(
                        "친구의 범죄를 신고해야 하는 근거",
                        3
                );

        assertThat(response).isNotNull();
        assertThat(response.requestId())
                .isEqualTo("request-123");

        assertThat(response.totalCount())
                .isEqualTo(1);

        assertThat(response.results())
                .hasSize(1);

        assertThat(response.results().get(0).title())
                .isEqualTo("테스트 기사");

        assertThat(response.results().get(0).url())
                .isEqualTo("https://example.com/article");

        server.verify();
    }

    @Test
    @DisplayName("검색어가 비어 있으면 요청하지 않는다")
    void blankQuery() {
        LinerClient client = client();

        assertThatThrownBy(
                () -> client.searchWeb(" ", 3)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "검색어는 비어 있을 수 없습니다"
                );

        server.verify();
    }

    @Test
    @DisplayName("검색 결과 개수는 1 이상 20 이하여야 한다")
    void invalidMaxResults() {
        LinerClient client = client();

        assertThatThrownBy(
                () -> client.searchWeb("테스트", 21)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "1 이상 20 이하"
                );

        server.verify();
    }

    @Test
    @DisplayName("API Key가 없으면 LINER를 호출하지 않는다")
    void apiKeyNotConfigured() {
        LinerClient client = clientWith(
                new LinerProperties(BASE_URL, "")
        );

        assertThatThrownBy(
                () -> client.searchWeb("테스트", 3)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LinerErrorCode.LINER_NOT_CONFIGURED);

        server.verify();
    }

    @Test
    @DisplayName("LINER 크레딧이 부족하면 LINER_CREDIT_EXHAUSTED")
    void creditExhausted() {
        LinerClient client = client();

        server.expect(
                        requestTo(BASE_URL + "/api/v1/tools/search/web")
                )
                .andRespond(
                        withStatus(HttpStatus.PAYMENT_REQUIRED)
                );

        assertThatThrownBy(
                () -> client.searchWeb("테스트", 3)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LinerErrorCode.LINER_CREDIT_EXHAUSTED);

        server.verify();
    }

    @Test
    @DisplayName("LINER 요청 제한에 걸리면 LINER_RATE_LIMITED")
    void rateLimited() {
        LinerClient client = client();

        server.expect(
                        requestTo(BASE_URL + "/api/v1/tools/search/web")
                )
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                );

        assertThatThrownBy(
                () -> client.searchWeb("테스트", 3)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LinerErrorCode.LINER_RATE_LIMITED);

        server.verify();
    }

    @Test
    @DisplayName("LINER 서버 오류면 LINER_PROVIDER_ERROR")
    void serverError() {
        LinerClient client = client();

        server.expect(
                        requestTo(BASE_URL + "/api/v1/tools/search/web")
                )
                .andRespond(
                        withServerError()
                );

        assertThatThrownBy(
                () -> client.searchWeb("테스트", 3)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LinerErrorCode.LINER_PROVIDER_ERROR);

        server.verify();
    }

    @Test
    @DisplayName("LINER 요청 형식이 잘못되면 LINER_INVALID_REQUEST")
    void badRequest() {
        LinerClient client = client();

        server.expect(
                        requestTo(BASE_URL + "/api/v1/tools/search/web")
                )
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                );

        assertThatThrownBy(
                () -> client.searchWeb("테스트", 3)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LinerErrorCode.LINER_INVALID_REQUEST);

        server.verify();
    }

    @Test
    @DisplayName("LINER API Key가 유효하지 않으면 LINER_NOT_CONFIGURED")
    void unauthorized() {
        LinerClient client = client();

        server.expect(
                        requestTo(BASE_URL + "/api/v1/tools/search/web")
                )
                .andRespond(
                        withStatus(HttpStatus.UNAUTHORIZED)
                );

        assertThatThrownBy(
                () -> client.searchWeb("테스트", 3)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LinerErrorCode.LINER_NOT_CONFIGURED);

        server.verify();
    }
}