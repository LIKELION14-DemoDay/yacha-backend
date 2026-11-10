package likelion.yacha_backend.domain.payment.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 웹훅: 결제 행을 잠그며 읽음 (SELECT ... FOR UPDATE)
     * Paddle 은 같은 이벤트를 다시 보낼 수 있어서, 두 요청이 겹쳐도 뒤 요청은 앞 요청의 상태를 보고 넘어감
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paddleTransactionId = :transactionId")
    Optional<Payment> findByPaddleTransactionIdForUpdate(@Param("transactionId") String transactionId);

    /** 관리자 환불: 결제 행을 잠그며 읽음. 같은 결제의 웹훅 처리와 겹치지 않게 함 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findByPaddleTransactionId(String paddleTransactionId);

    /** 관리자 조회: 회원의 결제 내역, 최근 것부터 */
    @Query("select p from Payment p join fetch p.user where p.user.id = :userId order by p.id desc")
    List<Payment> findAllByUserIdWithUser(@Param("userId") Long userId);

    @Query("select p from Payment p join fetch p.user where p.paddleTransactionId = :transactionId")
    Optional<Payment> findByPaddleTransactionIdWithUser(@Param("transactionId") String transactionId);

    @Query("select p from Payment p join fetch p.user where p.id = :id")
    Optional<Payment> findByIdWithUser(@Param("id") Long id);
}
