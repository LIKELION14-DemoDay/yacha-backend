package likelion.yacha_backend.domain.payment.repository;

import java.util.List;
import likelion.yacha_backend.domain.payment.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    boolean existsByEventId(String eventId);

    List<PaymentEvent> findByTransactionIdOrderByIdAsc(String transactionId);
}
