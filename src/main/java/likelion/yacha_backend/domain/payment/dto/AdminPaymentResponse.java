package likelion.yacha_backend.domain.payment.dto;

import java.time.LocalDateTime;
import likelion.yacha_backend.domain.payment.entity.Payment;
import likelion.yacha_backend.domain.payment.entity.PaymentProduct;
import likelion.yacha_backend.domain.payment.entity.PaymentStatus;
import likelion.yacha_backend.domain.user.entity.User;

/** 관리자 결제 조회 한 건. 금액은 통화의 최소 단위 문자열 (USD 10.00 → "1000") */
public record AdminPaymentResponse(
        Long paymentId,
        Long userId,
        String email,
        String nickname,
        PaymentProduct product,
        PaymentStatus status,
        String transactionId,
        String amount,
        String currencyCode,
        String invoiceNumber,
        String paddleCustomerId,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        String paddleAdjustmentId,
        String refundReason,
        Long refundRequestedBy,
        LocalDateTime refundRequestedAt,
        LocalDateTime refundedAt
) {

    public static AdminPaymentResponse from(Payment payment) {
        User user = payment.getUser();
        return new AdminPaymentResponse(
                payment.getId(),
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                payment.getProduct(),
                payment.getStatus(),
                payment.getPaddleTransactionId(),
                payment.getAmount(),
                payment.getCurrencyCode(),
                payment.getInvoiceNumber(),
                payment.getPaddleCustomerId(),
                payment.getCreatedAt(),
                payment.getCompletedAt(),
                payment.getPaddleAdjustmentId(),
                payment.getRefundReason(),
                payment.getRefundRequestedBy(),
                payment.getRefundRequestedAt(),
                payment.getRefundedAt()
        );
    }
}
