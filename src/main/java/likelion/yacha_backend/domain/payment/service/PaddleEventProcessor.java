package likelion.yacha_backend.domain.payment.service;

import java.nio.charset.StandardCharsets;
import likelion.yacha_backend.domain.payment.entity.PaymentEvent;
import likelion.yacha_backend.domain.payment.entity.PaymentEventResult;
import likelion.yacha_backend.domain.payment.repository.PaymentEventRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 서명이 확인된 Paddle 이벤트를 결제 로그(payment_events)에 원본 그대로 남기고 결제 완료 · 환불을 반영한다.
 *
 * <p>로그 저장과 반영을 한 트랜잭션으로 묶는다. 반영하다 실패하면 로그도 남지 않고 5xx 가 나가서
 * Paddle 이 다시 보내고, 그때 처음부터 다시 처리한다. 이미 저장된 event_id 는 처리하지 않는다.
 * Paddle 은 5초 안에 2xx 를 받지 못하면 다시 보내므로, 여기서는 DB 반영만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaddleEventProcessor {

    static final String TRANSACTION_COMPLETED = "transaction.completed";
    static final String ADJUSTMENT_CREATED = "adjustment.created";
    static final String ADJUSTMENT_UPDATED = "adjustment.updated";

    private final PaymentService paymentService;
    private final PaymentEventRepository paymentEventRepository;

    @Transactional
    public void process(JsonNode root, byte[] rawBody) {
        String eventId = text(root.path("event_id"));
        String eventType = text(root.path("event_type"));
        if (eventId == null || eventType == null) {
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_PAYLOAD);
        }
        if (paymentEventRepository.existsByEventId(eventId)) {
            log.info("[Paddle] 이미 받은 이벤트 eventId={} eventType={}", eventId, eventType);
            return;
        }

        JsonNode data = root.path("data");
        String transactionId = transactionId(eventType, data);

        PaymentEventResult result = switch (eventType) {
            case TRANSACTION_COMPLETED -> paymentService.completeTransaction(new PaymentService.CompletedTransaction(
                    require(transactionId),
                    text(data.path("details").path("totals").path("grand_total")),
                    text(data.path("currency_code")),
                    text(data.path("customer_id")),
                    text(data.path("invoice_number"))));
            case ADJUSTMENT_CREATED, ADJUSTMENT_UPDATED -> paymentService.applyAdjustment(
                    new PaymentService.PaddleAdjustment(
                            require(text(data.path("id"))),
                            require(transactionId),
                            text(data.path("action")),
                            text(data.path("status")),
                            text(data.path("reason"))));
            default -> PaymentEventResult.IGNORED;
        };

        paymentEventRepository.save(PaymentEvent.of(eventId, eventType, transactionId,
                text(root.path("occurred_at")), result, new String(rawBody, StandardCharsets.UTF_8)));
        log.info("[Paddle] 이벤트 처리 eventId={} eventType={} transactionId={} result={}",
                eventId, eventType, transactionId, result);
    }

    /** 거래 이벤트는 data.id, 환불 이벤트는 data.transaction_id. 그 밖은 거래 ID 처럼 보일 때만 남김 */
    private static String transactionId(String eventType, JsonNode data) {
        return switch (eventType) {
            case ADJUSTMENT_CREATED, ADJUSTMENT_UPDATED -> text(data.path("transaction_id"));
            default -> {
                String id = text(data.path("id"));
                yield id != null && id.startsWith("txn_") ? id : null;
            }
        };
    }

    private static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_PAYLOAD);
        }
        return value;
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }
}
