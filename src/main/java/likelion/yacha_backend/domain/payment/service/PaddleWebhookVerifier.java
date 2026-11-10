package likelion.yacha_backend.domain.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import likelion.yacha_backend.domain.payment.config.PaddleProperties;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Paddle 웹훅 서명 검증
 *
 * <p>헤더 {@code Paddle-Signature: ts=1671552777;h1=eb4d...}
 * {@code ts + ":" + 받은 본문 그대로} 를 웹훅 시크릿으로 HMAC-SHA256 한 hex 가 h1 과 같아야 한다.
 * 본문을 JSON 으로 읽었다 다시 쓰면 서명이 달라지므로 받은 바이트 그대로 검사한다.
 * 시크릿을 바꾸는 동안에는 h1 이 여러 개 올 수 있어 하나라도 맞으면 통과.
 */
@Component
@RequiredArgsConstructor
public class PaddleWebhookVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final PaddleProperties properties;
    private final Clock clock;

    public void verify(String signatureHeader, byte[] rawBody) {
        if (!properties.isWebhookConfigured()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        if (signatureHeader == null || signatureHeader.isBlank() || rawBody == null) {
            throw invalid();
        }

        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("ts".equals(pair[0])) {
                timestamp = pair[1];
            } else if ("h1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw invalid();
        }

        checkFreshness(timestamp);

        byte[] expected = hmac(timestamp, rawBody);
        for (String signature : signatures) {
            byte[] actual;
            try {
                actual = HexFormat.of().parseHex(signature);
            } catch (IllegalArgumentException e) {
                continue;
            }
            // 걸린 시간으로 맞는 글자 수를 짐작하지 못하게 끝까지 비교
            if (MessageDigest.isEqual(expected, actual)) {
                return;
            }
        }
        throw invalid();
    }

    /** 가로챈 웹훅을 나중에 다시 보내는 것을 막음. 같은 결제는 한 번만 처리하므로 허용 범위 안의 재전송은 무해 */
    private void checkFreshness(String timestamp) {
        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - sentAt) > properties.webhookTolerance().toSeconds()) {
            throw invalid();
        }
    }

    private byte[] hmac(String timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            mac.update((timestamp + ":").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(rawBody);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 을 계산하지 못했습니다.", e);
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }
}
