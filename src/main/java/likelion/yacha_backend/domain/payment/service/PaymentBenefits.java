package likelion.yacha_backend.domain.payment.service;

import likelion.yacha_backend.domain.payment.entity.Payment;
import likelion.yacha_backend.domain.user.entity.User;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 상품별 혜택 지급 · 회수. 사용자 행은 잠근 상태로 넘긴다.
 *
 * <ul>
 *   <li>PREMIUM_PASS: 결제하면 영구 프리미엄, 환불하면 바로 해제
 *   <li>ATTACK_TICKET: 결제하면 1개, 환불하면 1개 회수 (쓰기 전에만 환불)
 * </ul>
 */
@Slf4j
final class PaymentBenefits {

    private PaymentBenefits() {
    }

    static void grant(Payment payment, User user) {
        switch (payment.getProduct()) {
            case PREMIUM_PASS -> {
                // 결제창을 두 개 열어 둘 다 결제한 경우. 돈은 나갔으므로 완료로 기록하고 환불 대상으로 남김
                if (user.isPremium()) {
                    log.warn("[Paddle] 프리미엄 패스 중복 결제, 환불 필요 paymentId={} userId={} transactionId={}",
                            payment.getId(), user.getId(), payment.getPaddleTransactionId());
                }
                user.grantPremium();
            }
            case ATTACK_TICKET -> user.addAttackTicket();
        }
    }

    /** 환불 가능 여부. 공격권은 남은 게 있어야 함 */
    static boolean isRefundable(Payment payment, User user) {
        return switch (payment.getProduct()) {
            case PREMIUM_PASS -> true;
            case ATTACK_TICKET -> user.getAttackTickets() > 0;
        };
    }

    static void revoke(Payment payment, User user) {
        switch (payment.getProduct()) {
            case PREMIUM_PASS -> user.revokePremium();
            case ATTACK_TICKET -> {
                if (!user.removeAttackTicket()) {
                    // 대시보드 환불 · 차지백은 우리가 막을 수 없음. 이미 다 쓴 경우라 회수할 게 없음
                    log.warn("[Paddle] 이미 사용한 공격권이 환불됨, 회수할 공격권 없음 paymentId={} userId={}",
                            payment.getId(), user.getId());
                }
            }
        }
    }

    /** Paddle 이 환불을 거절해 요청 때 회수한 혜택을 돌려줌 */
    static void restore(Payment payment, User user) {
        switch (payment.getProduct()) {
            case PREMIUM_PASS -> user.grantPremium();
            case ATTACK_TICKET -> user.addAttackTicket();
        }
    }
}
