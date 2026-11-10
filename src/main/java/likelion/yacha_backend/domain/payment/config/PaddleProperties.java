package likelion.yacha_backend.domain.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paddle 결제 설정 (application.yaml 의 paddle.*)
 *
 * <p>샌드박스와 라이브는 계정 · 키 · 가격 ID 가 모두 따로라, 환경변수만 바꿔 전환한다.
 * 키가 비어 있어도 서버는 뜨고, 결제 요청만 PAYMENT_NOT_CONFIGURED 로 막는다.
 */
@ConfigurationProperties(prefix = "paddle")
public record PaddleProperties(
        String baseUrl,
        String apiKey,
        String webhookSecret,
        Duration webhookTolerance,
        Product premiumPass,
        Product attackTicket
) {

    public PaddleProperties {
        if (webhookTolerance == null) {
            webhookTolerance = Duration.ofSeconds(5);
        }
        if (premiumPass == null) {
            premiumPass = new Product(null);
        }
        if (attackTicket == null) {
            attackTicket = new Product(null);
        }
    }

    /**
     * 판매 상품 하나
     *
     * @param priceId Paddle 대시보드에서 만든 가격 ID ({@code pri_...}). 금액은 Paddle 에서 정함
     */
    public record Product(String priceId) {

        public boolean isConfigured() {
            return hasText(priceId);
        }
    }

    public boolean isApiConfigured() {
        return hasText(baseUrl) && hasText(apiKey);
    }

    public boolean isWebhookConfigured() {
        return hasText(webhookSecret);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
