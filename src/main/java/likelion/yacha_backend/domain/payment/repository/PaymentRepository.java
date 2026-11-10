package likelion.yacha_backend.domain.payment.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import likelion.yacha_backend.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 웹훅: 결제 행을 잠그며 읽음 (SELECT ... FOR UPDATE)
     * Paddle 은 같은 이벤트를 다시 보낼 수 있어서, 두 요청이 겹쳐도 뒤 요청은 앞 요청의 COMPLETED 를 보고 넘어감
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paddleTransactionId = :transactionId")
    Optional<Payment> findByPaddleTransactionIdForUpdate(@Param("transactionId") String transactionId);

    Optional<Payment> findByPaddleTransactionId(String paddleTransactionId);
}
