package likelion.yacha_backend.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 결제 한 건. 결제 요청(checkout) 때 Paddle 거래를 만들고 PENDING 으로 저장,
 * 웹훅으로 결제가 확인되면 COMPLETED 로 바꾸며 혜택을 준다.
 *
 * <p><b>지우지 않는다.</b> 환불 문의 · 분쟁 대응 근거라 회원이 탈퇴해도 남겨야 한다.
 * 탈퇴 기능을 만들 때 users 행을 지우면 이 행이 막거나 함께 사라지므로, 회원 정보를 비식별화하는 방식으로 처리할 것.
 */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentProduct product;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Paddle 거래 ID ({@code txn_...}). 웹훅이 이 값으로 결제를 찾음 */
    @Column(name = "paddle_transaction_id", nullable = false, unique = true, length = 50)
    private String paddleTransactionId;

    // ---- 결제 완료 웹훅에서 채움 ----

    /** 실제 결제 금액 (세금 포함, 통화의 최소 단위. USD 10.00 → "1000", KRW 는 원 단위) */
    @Column(length = 20)
    private String amount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    /** Paddle 고객 ID ({@code ctm_...}) */
    @Column(name = "paddle_customer_id", length = 50)
    private String paddleCustomerId;

    /** 영수증(인보이스) 번호. 사용자가 영수증을 들고 문의하면 이 값으로 찾음 */
    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ---- 환불 ----

    /** Paddle 환불 · 차지백 ID ({@code adj_...}) */
    @Column(name = "paddle_adjustment_id", length = 50)
    private String paddleAdjustmentId;

    @Column(name = "refund_reason", length = 255)
    private String refundReason;

    /** 환불을 요청한 관리자 id. Paddle 대시보드 환불 · 차지백이면 NULL */
    @Column(name = "refund_requested_by")
    private Long refundRequestedBy;

    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    public static Payment pending(User user, PaymentProduct product, String paddleTransactionId) {
        Payment payment = new Payment();
        payment.user = user;
        payment.product = product;
        payment.status = PaymentStatus.PENDING;
        payment.paddleTransactionId = paddleTransactionId;
        return payment;
    }

    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }

    public void complete(LocalDateTime now, String amount, String currencyCode,
                         String paddleCustomerId, String invoiceNumber) {
        requireStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.COMPLETED;
        this.completedAt = now;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.paddleCustomerId = paddleCustomerId;
        this.invoiceNumber = invoiceNumber;
    }

    /** 관리자 환불 요청. Paddle 승인 전이라 REFUND_PENDING */
    public void requestRefund(String adjustmentId, String reason, Long adminId, LocalDateTime now) {
        requireStatus(PaymentStatus.COMPLETED);
        this.status = PaymentStatus.REFUND_PENDING;
        this.paddleAdjustmentId = adjustmentId;
        this.refundReason = reason;
        this.refundRequestedBy = adminId;
        this.refundRequestedAt = now;
    }

    /** 관리자가 요청한 환불이 승인됨 */
    public void markRefunded(LocalDateTime now) {
        requireStatus(PaymentStatus.REFUND_PENDING);
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = now;
    }

    /** 우리 API 를 거치지 않은 환불 · 차지백이 승인됨 (Paddle 대시보드에서 직접 환불 등) */
    public void markRefundedExternally(String adjustmentId, String reason, LocalDateTime now) {
        requireStatus(PaymentStatus.COMPLETED);
        this.status = PaymentStatus.REFUNDED;
        this.paddleAdjustmentId = adjustmentId;
        this.refundReason = reason;
        this.refundRequestedAt = now;
        this.refundedAt = now;
    }

    /** Paddle 이 환불을 거절함. 요청 기록은 사유에 남기고 결제 완료 상태로 되돌림 */
    public void rejectRefund() {
        requireStatus(PaymentStatus.REFUND_PENDING);
        this.status = PaymentStatus.COMPLETED;
        this.refundReason = "[거절됨] " + refundReason;
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "결제 상태가 " + expected + " 가 아닙니다. paymentId=" + id + " status=" + status);
        }
    }
}
