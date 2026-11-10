package likelion.yacha_backend.domain.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Paddle 설정")
class PaddlePropertiesTest {

    private static PaddleProperties withTolerance(Duration tolerance) {
        return new PaddleProperties("https://sandbox-api.paddle.com", "key", "secret", tolerance, null, null);
    }

    @Test
    @DisplayName("웹훅 허용 시간이 없으면 5초")
    void defaultTolerance() {
        assertThat(withTolerance(null).webhookTolerance()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("웹훅 허용 시간이 0 이하면 부팅 실패 (모든 웹훅이 조용히 거부되는 것을 막음)")
    void rejectsNonPositiveTolerance() {
        assertThatThrownBy(() -> withTolerance(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withTolerance(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("키 · 시크릿 · 가격 ID 가 비어 있어도 만들어짐 (결제 요청에서만 막음)")
    void allowsMissingKeys() {
        PaddleProperties properties = new PaddleProperties("https://sandbox-api.paddle.com", "", "", null, null, null);

        assertThat(properties.isApiConfigured()).isFalse();
        assertThat(properties.isWebhookConfigured()).isFalse();
        assertThat(properties.premiumPass().isConfigured()).isFalse();
    }
}
