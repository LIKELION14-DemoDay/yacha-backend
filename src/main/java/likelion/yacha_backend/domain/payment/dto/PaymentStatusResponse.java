package likelion.yacha_backend.domain.payment.dto;

import likelion.yacha_backend.domain.user.entity.User;

/**
 * @param premium       프리미엄 패스를 가졌는지 (게임 무제한, 만료 없음)
 * @param attackTickets 남은 채팅 공격권 수
 */
public record PaymentStatusResponse(
        boolean premium,
        int attackTickets
) {

    public static PaymentStatusResponse from(User user) {
        return new PaymentStatusResponse(user.isPremium(), user.getAttackTickets());
    }
}
