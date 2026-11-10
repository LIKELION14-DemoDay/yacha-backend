package likelion.yacha_backend.domain.payment.dto;

import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.payment.entity.PaymentEvent;
import likelion.yacha_backend.domain.payment.entity.PaymentEventResult;

/**
 * 관리자 결제 상세. 결제 · 회원의 지금 혜택 · 환불 가능 여부 · 결제 로그(웹훅 원본)
 *
 * @param refundable        지금 환불할 수 있는지
 * @param refundBlockedCode 환불할 수 없으면 그 이유 (REFUND_NOT_ALLOWED · ATTACK_TICKET_ALREADY_USED), 가능하면 null
 */
public record AdminPaymentDetailResponse(
        AdminPaymentResponse payment,
        boolean userPremium,
        int userAttackTickets,
        boolean refundable,
        String refundBlockedCode,
        List<EventResponse> events
) {

    public record EventResponse(
            String eventId,
            String eventType,
            PaymentEventResult result,
            String occurredAt,
            LocalDateTime receivedAt,
            String payload
    ) {

        public static EventResponse from(PaymentEvent event) {
            return new EventResponse(event.getEventId(), event.getEventType(), event.getResult(),
                    event.getOccurredAt(), event.getCreatedAt(), event.getPayload());
        }
    }
}
