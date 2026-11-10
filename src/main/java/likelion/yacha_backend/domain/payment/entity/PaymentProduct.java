package likelion.yacha_backend.domain.payment.entity;

import likelion.yacha_backend.domain.payment.config.PaddleProperties;

/** 판매 상품. 둘 다 한 번 결제하는 상품이고 자동 갱신은 없음 */
public enum PaymentProduct {

    /** 프리미엄 패스. 한 번 사면 게임을 영구히 무제한으로 이용. 이미 가진 사람은 다시 살 수 없음 */
    PREMIUM_PASS,

    /** 채팅 공격권. 한 번 결제에 1개 */
    ATTACK_TICKET;

    public PaddleProperties.Product settings(PaddleProperties properties) {
        return switch (this) {
            case PREMIUM_PASS -> properties.premiumPass();
            case ATTACK_TICKET -> properties.attackTicket();
        };
    }
}
