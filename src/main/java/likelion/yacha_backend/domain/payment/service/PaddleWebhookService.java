package likelion.yacha_backend.domain.payment.service;

import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Paddle 웹훅 처리. 서명을 확인한 뒤 결제 완료 이벤트만 반영하고 나머지는 무시한다.
 *
 * <p>Paddle 은 5초 안에 2xx 를 받지 못하면 다시 보내므로, 여기서는 DB 반영만 하고 바로 돌려준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaddleWebhookService {

    static final String TRANSACTION_COMPLETED = "transaction.completed";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PaddleWebhookVerifier verifier;
    private final PaymentService paymentService;

    public void handle(String signatureHeader, byte[] rawBody) {
        verifier.verify(signatureHeader, rawBody);

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(rawBody);
        } catch (JacksonException e) {
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_PAYLOAD);
        }

        String eventType = text(root.path("event_type"));
        String eventId = text(root.path("event_id"));
        if (!TRANSACTION_COMPLETED.equals(eventType)) {
            log.debug("[Paddle] 처리하지 않는 이벤트 eventId={} eventType={}", eventId, eventType);
            return;
        }

        String transactionId = text(root.path("data").path("id"));
        if (transactionId == null || transactionId.isBlank()) {
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_PAYLOAD);
        }
        log.info("[Paddle] 결제 완료 이벤트 eventId={} transactionId={}", eventId, transactionId);
        paymentService.completeTransaction(transactionId);
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }
}
