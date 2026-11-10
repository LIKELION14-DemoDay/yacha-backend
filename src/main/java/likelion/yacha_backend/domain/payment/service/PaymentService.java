package likelion.yacha_backend.domain.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.payment.client.PaddleClient;
import likelion.yacha_backend.domain.payment.config.PaddleProperties;
import likelion.yacha_backend.domain.payment.dto.CheckoutResponse;
import likelion.yacha_backend.domain.payment.dto.PaymentStatusResponse;
import likelion.yacha_backend.domain.payment.entity.Payment;
import likelion.yacha_backend.domain.payment.entity.PaymentProduct;
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
     * 결제 완료 (웹훅 transaction.completed). 혜택을 주고 COMPLETED 로 바꾼다.
     *
     * <p>같은 이벤트가 다시 와도 이미 COMPLETED 면 넘어가서 혜택이 두 번 들어가지 않는다.
     * 우리가 만들지 않은 거래(대시보드에서 직접 만든 테스트 거래 등)는 기록만 남기고 넘어간다.
     */
    @Transactional
    public void completeTransaction(String transactionId) {
        Payment payment = paymentRepository.findByPaddleTransactionIdForUpdate(transactionId).orElse(null);
        if (payment == null) {
            log.warn("[Paddle] 우리 결제에 없는 거래가 완료됨 transactionId={}", transactionId);
            return;
        }
        if (payment.isCompleted()) {
            log.info("[Paddle] 이미 처리한 결제 transactionId={}", transactionId);
            return;
        }

        // 같은 사용자의 다른 결제 웹훅이 겹쳐도 공격권 수를 덮어쓰지 않게 사용자 행도 잠금
        User user = userRepository.findByIdForUpdate(payment.getUser().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "결제한 사용자가 없습니다. paymentId=" + payment.getId()));

        switch (payment.getProduct()) {
            case PREMIUM_PASS -> {
                // 결제창을 두 개 열어 둘 다 결제한 경우. 돈은 나갔으므로 완료로 기록하고 환불 대상으로 남김
                if (user.isPremium()) {
                    log.warn("[Paddle] 프리미엄 패스 중복 결제, 환불 필요 paymentId={} userId={} transactionId={}",
                            payment.getId(), user.getId(), transactionId);
                }
                user.grantPremium();
            }
            case ATTACK_TICKET -> user.addAttackTicket();
        }
        payment.complete(LocalDateTime.now(clock));
        log.info("[Paddle] 결제 완료 paymentId={} userId={} product={}",
                payment.getId(), user.getId(), payment.getProduct());
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getMyStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        return PaymentStatusResponse.from(user);
    }
}
