package likelion.yacha_backend.domain.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Map;
import likelion.yacha_backend.domain.payment.config.PaddleProperties;
import likelion.yacha_backend.domain.payment.service.PaymentErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Paddle 거래 생성 클라이언트")
class PaddleClientTest {

    private static final String BASE_URL = "https://sandbox-api.paddle.com";

    private MockRestServiceServer server;

    private PaddleClient clientWith(String apiKey) {
        PaddleProperties properties = new PaddleProperties(
                BASE_URL, apiKey, "secret", Duration.ofSeconds(5), null, null);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new PaddleClient(properties, builder);
    }

    @Test
    @DisplayName("가격 ID 와 custom_data 로 거래를 만들고 거래 ID 를 돌려줌")
    void createTransaction() {
        PaddleClient client = clientWith("pdl_sdbx_apikey_test");
        server.expect(requestTo(BASE_URL + "/transactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer pdl_sdbx_apikey_test"))
                .andExpect(content().json("""
                        {"items": [{"price_id": "pri_premium", "quantity": 1}],
                         "custom_data": {"user_id": "7"}}
                        """))
                .andRespond(withSuccess("""
                        {"data": {"id": "txn_01abc", "status": "ready"}, "meta": {}}
                        """, MediaType.APPLICATION_JSON));

        String transactionId = client.createTransaction("pri_premium", Map.of("user_id", "7"));

        assertThat(transactionId).isEqualTo("txn_01abc");
        server.verify();
    }

    @Test
    @DisplayName("API 키가 없으면 Paddle 을 부르지 않고 PAYMENT_NOT_CONFIGURED")
    void notConfigured() {
        PaddleClient client = clientWith("");

        assertThatThrownBy(() -> client.createTransaction("pri_premium", Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_CONFIGURED);
        server.verify();
    }

    @Test
    @DisplayName("Paddle 이 4xx · 5xx 로 거부하면 PAYMENT_PROVIDER_ERROR")
    void providerError() {
        PaddleClient client = clientWith("key");
        server.expect(requestTo(BASE_URL + "/transactions")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.createTransaction("pri_premium", Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);

        PaddleClient again = clientWith("key");
        server.expect(requestTo(BASE_URL + "/transactions")).andRespond(withServerError());

        assertThatThrownBy(() -> again.createTransaction("pri_premium", Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("응답에 data.id 가 없으면 PAYMENT_PROVIDER_ERROR")
    void missingTransactionId() {
        PaddleClient client = clientWith("key");
        server.expect(requestTo(BASE_URL + "/transactions"))
                .andRespond(withSuccess("{\"data\": {}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createTransaction("pri_premium", Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
    }
}
