package likelion.yacha_backend.domain.payment.entity;

public enum PaymentEventResult {

    /** 결제 상태 · 혜택에 반영함 */
    APPLIED,

    /** 받았지만 바꿀 것이 없음 (처리하지 않는 이벤트, 모르는 거래, 이미 반영된 상태 등) */
    IGNORED,
}
