package likelion.yacha_backend.domain.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.payment.client.PaddleClient;
import likelion.yacha_backend.domain.payment.dto.AdminPaymentDetailResponse;
import likelion.yacha_backend.domain.payment.dto.AdminPaymentResponse;
import likelion.yacha_backend.domain.payment.entity.Payment;
import likelion.yacha_backend.domain.payment.repository.PaymentEventRepository;
import likelion.yacha_backend.domain.payment.repository.PaymentRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 결제 조회 · 환불 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAdminService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final UserRepository userRepository;
    private final PaddleClient paddleClient;
    private final Clock clock;

    /** 거래 ID → 회원 id → 이메일 순으로 먼저 들어온 조건 하나로 찾음. 최근 결제부터 */
    @Transactional(readOnly = true)
    public List<AdminPaymentResponse> search(Long userId, String email, String transactionId) {
        if (hasText(transactionId)) {
            return paymentRepository.findByPaddleTransactionIdWithUser(transactionId.trim())
                    .map(AdminPaymentResponse::from)
                    .map(List::of)
                    .orElse(List.of());
        }
        if (userId != null) {
            return toResponses(paymentRepository.findAllByUserIdWithUser(userId));
        }
        if (hasText(email)) {
            return userRepository.findByEmail(email.trim())
                    .map(user -> toResponses(paymentRepository.findAllByUserIdWithUser(user.getId())))
                    .orElse(List.of());
        }
        throw new BusinessException(PaymentErrorCode.SEARCH_CONDITION_REQUIRED);
    }

    @Transactional(readOnly = true)
    public AdminPaymentDetailResponse getDetail(Long paymentId) {
        Payment payment = paymentRepository.findByIdWithUser(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        User user = payment.getUser();
        PaymentErrorCode blocked = refundBlockedReason(payment, user);

        return new AdminPaymentDetailResponse(
                AdminPaymentResponse.from(payment),
                user.isPremium(),
                user.getAttackTickets(),
                blocked == null,
                blocked == null ? null : blocked.name(),
                paymentEventRepository.findByTransactionIdOrderByIdAsc(payment.getPaddleTransactionId()).stream()
                        .map(AdminPaymentDetailResponse.EventResponse::from)
                        .toList()
        );
    }

    /**
     * 환불 요청. 혜택을 먼저 회수하고 Paddle 에 전체 환불을 요청한다.
     *
     * <ul>
     *   <li>프리미엄 패스: 바로 해제
     *   <li>공격권: 쓰기 전에만 환불. 남은 공격권이 없으면 ATTACK_TICKET_ALREADY_USED
     * </ul>
     *
     * <p>Paddle 승인을 기다리는 동안 공격권을 써 버리지 않게 승인 전에 회수한다. 거절되면 웹훅에서 돌려준다.
     * 결제 · 사용자 행을 잠근 채 Paddle 을 불러, 같은 결제의 환불 웹훅은 이 요청이 커밋될 때까지 기다린다.
     * Paddle 호출이 실패하면 혜택 · 상태는 그대로다.
     */
    @Transactional
    public AdminPaymentResponse refund(Long paymentId, Long adminId, String reason) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.isCompleted()) {
            throw new BusinessException(PaymentErrorCode.REFUND_NOT_ALLOWED);
        }
        User user = userRepository.findByIdForUpdate(payment.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("결제한 사용자가 없습니다. paymentId=" + paymentId));
        if (!PaymentBenefits.isRefundable(payment, user)) {
            throw new BusinessException(PaymentErrorCode.ATTACK_TICKET_ALREADY_USED);
        }

        // 실패하면 아무것도 바꾸지 않은 채 끝나도록 Paddle 을 먼저 부름. 잠금 안이라 그 사이 공격권을 쓸 수 없음
        PaddleClient.RefundResult result = paddleClient.createFullRefund(payment.getPaddleTransactionId(), reason);
        PaymentBenefits.revoke(payment, user);

        LocalDateTime now = LocalDateTime.now(clock);
        payment.requestRefund(result.adjustmentId(), reason, adminId, now);
        if (result.isApproved()) {
            payment.markRefunded(now);
        }
        log.info("[Paddle] 관리자 환불 요청 paymentId={} adminId={} adjustmentId={} status={}",
                paymentId, adminId, result.adjustmentId(), result.status());
        return AdminPaymentResponse.from(payment);
    }

    private static PaymentErrorCode refundBlockedReason(Payment payment, User user) {
        if (!payment.isCompleted()) {
            return PaymentErrorCode.REFUND_NOT_ALLOWED;
        }
        if (!PaymentBenefits.isRefundable(payment, user)) {
            return PaymentErrorCode.ATTACK_TICKET_ALREADY_USED;
        }
        return null;
    }

    private static List<AdminPaymentResponse> toResponses(List<Payment> payments) {
        return payments.stream().map(AdminPaymentResponse::from).toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
