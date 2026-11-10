package likelion.yacha_backend.domain.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.payment.client.PaddleClient;
import likelion.yacha_backend.domain.payment.config.PaddleProperties;
import likelion.yacha_backend.domain.payment.dto.CheckoutResponse;
import likelion.yacha_backend.domain.payment.dto.PaymentStatusResponse;
import likelion.yacha_backend.domain.payment.entity.Payment;
import likelion.yacha_backend.domain.payment.entity.PaymentEventResult;
import likelion.yacha_backend.domain.payment.entity.PaymentProduct;
import likelion.yacha_backend.domain.payment.entity.PaymentStatus;
import likelion.yacha_backend.domain.payment.repository.PaymentRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 혜택을 회수하는 Paddle adjustment action. 차지백도 돈이 돌아가므로 환불과 같게 처리 */
    private static final Set<String> REFUND_ACTIONS = Set.of("refund", "chargeback", "chargeback_warning");

    private final PaddleClient paddleClient;
    private final PaddleProperties properties;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 결제 요청. Paddle 거래를 만들고 PENDING 으로 저장한다.
     *
     * <p>Paddle 호출(최대 10초)이 DB 커넥션을 붙잡지 않게 트랜잭션 밖에서 부르고, 저장만 따로 한다.
     * 결제창을 닫으면 PENDING 으로 남고 혜택은 없다. 다시 사면 새 거래를 만든다.
     */
    public CheckoutResponse checkout(Long userId, PaymentProduct product) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        if (product == PaymentProduct.PREMIUM_PASS && user.isPremium()) {
            throw new BusinessException(PaymentErrorCode.ALREADY_PREMIUM);
        }

        PaddleProperties.Product settings = product.settings(properties);
        if (!settings.isConfigured()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_CONFIGURED);
        }

        // 웹훅은 거래 ID 로 결제를 찾지만, 저장 전에 실패했을 때 Paddle 대시보드에서 누구 결제인지 찾을 수 있게 남김
        String transactionId = paddleClient.createTransaction(settings.priceId(), Map.of(
                "user_id", String.valueOf(user.getId()),
                "product", product.name()
        ));

        Payment payment = paymentRepository.save(Payment.pending(user, product, transactionId));
        return new CheckoutResponse(payment.getId(), transactionId);
    }

    /**
     * 결제 완료 (웹훅 transaction.completed). 혜택을 주고 금액 · 영수증 번호와 함께 COMPLETED 로 바꾼다.
     *
     * <p>같은 이벤트가 다시 와도 PENDING 이 아니면 넘어가서 혜택이 두 번 들어가지 않는다.
     * 우리가 만들지 않은 거래(대시보드에서 직접 만든 테스트 거래 등)는 기록만 남기고 넘어간다.
     */
    @Transactional
    public PaymentEventResult completeTransaction(CompletedTransaction transaction) {
        Payment payment = paymentRepository.findByPaddleTransactionIdForUpdate(transaction.transactionId())
                .orElse(null);
        if (payment == null) {
            log.warn("[Paddle] 우리 결제에 없는 거래가 완료됨 transactionId={}", transaction.transactionId());
            return PaymentEventResult.IGNORED;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("[Paddle] 이미 처리한 결제 transactionId={} status={}",
                    transaction.transactionId(), payment.getStatus());
            return PaymentEventResult.IGNORED;
        }

        // 같은 사용자의 다른 결제 웹훅이 겹쳐도 공격권 수를 덮어쓰지 않게 사용자 행도 잠금
        User user = lockUser(payment);
        PaymentBenefits.grant(payment, user);
        payment.complete(LocalDateTime.now(clock), transaction.amount(), transaction.currencyCode(),
                transaction.customerId(), transaction.invoiceNumber());
        log.info("[Paddle] 결제 완료 paymentId={} userId={} product={} amount={} {}",
                payment.getId(), user.getId(), payment.getProduct(), transaction.amount(), transaction.currencyCode());
        return PaymentEventResult.APPLIED;
    }

    /**
     * 환불 · 차지백 (웹훅 adjustment.created · adjustment.updated)
     *
     * <ul>
     *   <li>승인 + REFUND_PENDING: 관리자가 요청한 환불이 승인됨 → REFUNDED (혜택은 요청 때 이미 회수)
     *   <li>승인 + COMPLETED: 우리 API 를 거치지 않은 환불 · 차지백 → 혜택 회수 후 REFUNDED
     *   <li>거절 + REFUND_PENDING: 요청 때 회수한 혜택을 돌려주고 COMPLETED
     *   <li>그 밖 (승인 대기, 차지백 취소 등): 기록만 남김. 차지백 취소는 관리자가 직접 확인
     * </ul>
     */
    @Transactional
    public PaymentEventResult applyAdjustment(PaddleAdjustment adjustment) {
        if (!REFUND_ACTIONS.contains(adjustment.action())) {
            log.warn("[Paddle] 처리하지 않는 adjustment, 직접 확인 필요 adjustmentId={} action={} status={}",
                    adjustment.adjustmentId(), adjustment.action(), adjustment.status());
            return PaymentEventResult.IGNORED;
        }

        Payment payment = paymentRepository.findByPaddleTransactionIdForUpdate(adjustment.transactionId())
                .orElse(null);
        if (payment == null) {
            log.warn("[Paddle] 우리 결제에 없는 거래의 환불 transactionId={} adjustmentId={}",
                    adjustment.transactionId(), adjustment.adjustmentId());
            return PaymentEventResult.IGNORED;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (adjustment.isApproved()) {
            if (payment.getStatus() == PaymentStatus.REFUND_PENDING) {
                payment.markRefunded(now);
                log.info("[Paddle] 환불 승인 paymentId={} adjustmentId={}", payment.getId(), adjustment.adjustmentId());
                return PaymentEventResult.APPLIED;
            }
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                PaymentBenefits.revoke(payment, lockUser(payment));
                payment.markRefundedExternally(adjustment.adjustmentId(),
                        "[" + adjustment.action() + "] " + nullToEmpty(adjustment.reason()), now);
                log.warn("[Paddle] 우리 API 밖에서 환불 · 차지백 승인, 혜택 회수 paymentId={} adjustmentId={} action={}",
                        payment.getId(), adjustment.adjustmentId(), adjustment.action());
                return PaymentEventResult.APPLIED;
            }
            return PaymentEventResult.IGNORED;
        }

        if (adjustment.isRejected() && payment.getStatus() == PaymentStatus.REFUND_PENDING
                && adjustment.adjustmentId().equals(payment.getPaddleAdjustmentId())) {
            PaymentBenefits.restore(payment, lockUser(payment));
            payment.rejectRefund();
            log.warn("[Paddle] 환불 거절, 혜택 복구 paymentId={} adjustmentId={}",
                    payment.getId(), adjustment.adjustmentId());
            return PaymentEventResult.APPLIED;
        }
        return PaymentEventResult.IGNORED;
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getMyStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        return PaymentStatusResponse.from(user);
    }

    private User lockUser(Payment payment) {
        return userRepository.findByIdForUpdate(payment.getUser().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "결제한 사용자가 없습니다. paymentId=" + payment.getId()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** transaction.completed 에서 꺼낸 값. 금액은 details.totals.grand_total (최소 단위 문자열) */
    public record CompletedTransaction(
            String transactionId,
            String amount,
            String currencyCode,
            String customerId,
            String invoiceNumber
    ) {
    }

    /** adjustment.created · adjustment.updated 에서 꺼낸 값 */
    public record PaddleAdjustment(
            String adjustmentId,
            String transactionId,
            String action,
            String status,
            String reason
    ) {

        boolean isApproved() {
            return "approved".equals(status);
        }

        boolean isRejected() {
            return "rejected".equals(status);
        }
    }
}
