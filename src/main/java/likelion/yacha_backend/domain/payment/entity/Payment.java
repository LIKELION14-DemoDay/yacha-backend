package likelion.yacha_backend.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 */
@Entity
@Table(name = "payments")
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

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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

    public void complete(LocalDateTime now) {
        if (isCompleted()) {
            throw new IllegalStateException("이미 완료된 결제입니다. paymentId=" + id);
        }
        this.status = PaymentStatus.COMPLETED;
        this.completedAt = now;
    }
}
