package likelion.yacha_backend.domain.payment.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import likelion.yacha_backend.domain.payment.config.PaddleProperties;
import likelion.yacha_backend.domain.payment.service.PaymentErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Paddle Billing API 호출. 공식 Java SDK 가 없어 REST 로 직접 부른다.
 *
 * <p>샌드박스는 {@code https://sandbox-api.paddle.com}, 라이브는 {@code https://api.paddle.com}.
 * 키도 환경마다 따로라 base-url 과 api-key 를 같이 바꿔야 한다.
 */
@Component
public class PaddleClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final PaddleProperties properties;
    private final RestClient restClient;

    @Autowired
    public PaddleClient(PaddleProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory()));
    }

    /** 테스트에서 MockRestServiceServer 를 붙인 builder 를 넣으려고 분리한 생성자 */
    PaddleClient(PaddleProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            builder.baseUrl(properties.baseUrl());
        }
        // 없으면 계정 기본 버전을 씀. 버전이 올라가도 응답 모양이 바뀌지 않게 고정
        builder.defaultHeader("Paddle-Version", "1");
        this.restClient = builder.build();
    }

    /**
     * 결제할 거래를 만든다. 프론트는 돌려준 ID 로 Paddle.js 결제창을 연다
     * ({@code Paddle.Checkout.open({ transactionId })}).
     *
     * <p>가격과 사용자를 서버가 정해 두므로, 프론트가 다른 상품 · 다른 사용자로 바꿔 결제할 수 없다.
     *
     * @return Paddle 거래 ID ({@code txn_...})
     */
    public String createTransaction(String priceId, Map<String, String> customData) {
        if (!properties.isApiConfigured()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_CONFIGURED);
        }

        Map<String, Object> body = Map.of(
                "items", List.of(Map.of("price_id", priceId, "quantity", 1)),
                "custom_data", customData
        );

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null
                    && response.get("data") instanceof Map<?, ?> data
                    && data.get("id") instanceof String transactionId
                    && !transactionId.isBlank()) {
                return transactionId;
            }
            throw new BusinessException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR,
                    new IllegalStateException("Paddle 거래 생성 응답에 data.id 가 없습니다."));

        } catch (RestClientException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR, e);
        }
    }

    /**
     * 거래 전체 환불을 요청한다 ({@code POST /adjustments}, action=refund, type=full).
     *
     * <p>라이브는 대부분 Paddle 승인을 기다리는 {@code pending_approval} 로 돌아오고,
     * 승인 · 거절은 adjustment.updated 웹훅으로 온다. 샌드박스는 10분마다 자동 승인.
     */
    public RefundResult createFullRefund(String transactionId, String reason) {
        if (!properties.isApiConfigured()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_CONFIGURED);
        }

        Map<String, Object> body = Map.of(
                "action", "refund",
                "type", "full",
                "transaction_id", transactionId,
                "reason", reason
        );

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/adjustments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null
                    && response.get("data") instanceof Map<?, ?> data
                    && data.get("id") instanceof String adjustmentId
                    && data.get("status") instanceof String status) {
                return new RefundResult(adjustmentId, status);
            }
            throw new BusinessException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR,
                    new IllegalStateException("Paddle 환불 응답에 data.id · status 가 없습니다."));

        } catch (RestClientException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR, e);
        }
    }

    /**
     * @param adjustmentId Paddle 환불 ID ({@code adj_...})
     * @param status       pending_approval · approved · rejected · reversed
     */
    public record RefundResult(String adjustmentId, String status) {

        public boolean isApproved() {
            return "approved".equals(status);
        }
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
