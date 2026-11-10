package likelion.yacha_backend.domain.payment.dto;

/**
 * @param paymentId     우리 결제 ID
 * @param transactionId Paddle 거래 ID. 프론트는 {@code Paddle.Checkout.open({ transactionId })} 로 결제창을 연다
 */
public record CheckoutResponse(
        Long paymentId,
        String transactionId
) {
}
