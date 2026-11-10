package likelion.yacha_backend.domain.payment.entity;

public enum PaymentStatus {

    /** Paddle 거래를 만들고 결제를 기다림. 결제창을 닫거나 카드가 거절되면 이 상태로 남음 */
    PENDING,

    /** Paddle 웹훅(transaction.completed)으로 결제가 확인되고 혜택을 줌 */
    COMPLETED,

    /**
     * 관리자가 환불을 요청해 Paddle 승인을 기다림. 혜택은 요청할 때 이미 회수함
     * (기다리는 동안 공격권을 써 버리지 않게). Paddle 이 거절하면 혜택을 돌려주고 COMPLETED 로 돌아감
     */
    REFUND_PENDING,

    /** 환불(또는 차지백)이 승인됨. 혜택은 회수된 상태 */
    REFUNDED,
}
