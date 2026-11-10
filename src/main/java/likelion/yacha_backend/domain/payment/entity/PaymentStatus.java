package likelion.yacha_backend.domain.payment.entity;

public enum PaymentStatus {

    /** Paddle 거래를 만들고 결제를 기다림. 결제창을 닫거나 카드가 거절되면 이 상태로 남음 */
    PENDING,

    /** Paddle 웹훅(transaction.completed)으로 결제가 확인되고 혜택을 줌 */
    COMPLETED,
}
