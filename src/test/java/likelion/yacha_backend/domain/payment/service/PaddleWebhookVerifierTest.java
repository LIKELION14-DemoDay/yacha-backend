package likelion.yacha_backend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import likelion.yacha_backend.domain.payment.config.PaddleProperties;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Paddle 웹훅 서명 검증")
class PaddleWebhookVerifierTest {

    private static final String SECRET = "pdl_ntfset_test_secret";
    private static final long NOW = 1_700_000_000L;
    private static final byte[] BODY =
            "{\"event_type\":\"transaction.completed\",\"data\":{\"id\":\"txn_1\"}}".getBytes(StandardCharsets.UTF_8);

    private final PaddleWebhookVerifier verifier = verifierWith(SECRET);

    private static PaddleWebhookVerifier verifierWith(String secret) {
        PaddleProperties properties = new PaddleProperties(
                "https://sandbox-api.paddle.com", "key", secret, Duration.ofSeconds(5), null, null);
        return new PaddleWebhookVerifier(properties, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneId.of("Asia/Seoul")));
    }

    static String sign(String secret, long ts, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((ts + ":").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }

    @Test
    @DisplayName("본문과 시크릿이 맞으면 통과")
    void validSignature() throws Exception {
        String header = "ts=" + NOW + ";h1=" + sign(SECRET, NOW, BODY);

        assertThatCode(() -> verifier.verify(header, BODY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시크릿을 바꾸는 중이라 h1 이 여러 개면 하나만 맞아도 통과")
    void oneOfManySignatures() throws Exception {
        String header = "ts=" + NOW + ";h1=" + sign("old_secret", NOW, BODY) + ";h1=" + sign(SECRET, NOW, BODY);

        assertThatCode(() -> verifier.verify(header, BODY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("본문이 한 글자라도 바뀌면 거부")
    void tamperedBody() throws Exception {
        String header = "ts=" + NOW + ";h1=" + sign(SECRET, NOW, BODY);
        byte[] tampered = "{\"event_type\":\"transaction.completed\",\"data\":{\"id\":\"txn_2\"}}"
                .getBytes(StandardCharsets.UTF_8);

        assertInvalid(() -> verifier.verify(header, tampered));
    }

    @Test
    @DisplayName("다른 시크릿으로 서명하면 거부")
    void wrongSecret() throws Exception {
        String header = "ts=" + NOW + ";h1=" + sign("other_secret", NOW, BODY);

        assertInvalid(() -> verifier.verify(header, BODY));
    }

    @Test
    @DisplayName("서명 시각이 허용 범위(5초)를 넘으면 거부")
    void staleTimestamp() throws Exception {
        long old = NOW - 6;
        String header = "ts=" + old + ";h1=" + sign(SECRET, old, BODY);

        assertInvalid(() -> verifier.verify(header, BODY));
    }

    @Test
    @DisplayName("헤더가 없거나 형식이 틀리면 거부")
    void malformedHeader() {
        assertInvalid(() -> verifier.verify(null, BODY));
        assertInvalid(() -> verifier.verify("", BODY));
        assertInvalid(() -> verifier.verify("ts=" + NOW, BODY));
        assertInvalid(() -> verifier.verify("h1=abcd", BODY));
        assertInvalid(() -> verifier.verify("ts=abc;h1=abcd", BODY));
        assertInvalid(() -> verifier.verify("ts=" + NOW + ";h1=not-hex", BODY));
    }

    @Test
    @DisplayName("웹훅 시크릿이 설정되지 않았으면 PAYMENT_NOT_CONFIGURED")
    void notConfigured() {
        assertThatThrownBy(() -> verifierWith("").verify("ts=1;h1=ab", BODY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_CONFIGURED);
    }
}
