package likelion.yacha_backend.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import likelion.yacha_backend.domain.payment.client.PaddleClient;
import likelion.yacha_backend.domain.payment.entity.Payment;
import likelion.yacha_backend.domain.payment.entity.PaymentProduct;
import likelion.yacha_backend.domain.payment.entity.PaymentStatus;
import likelion.yacha_backend.domain.payment.repository.PaymentRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 웹훅이 겹쳐 들어와도 혜택이 한 번만 들어가는지. 결제 · 사용자 행 잠금이 실제로 동작해야 하므로
 * 테스트 트랜잭션을 쓰지 않고(요청마다 각자 커밋), 만든 행은 끝나고 지운다.
 *
 * <p>설정은 {@link PaymentApiTest} 와 같게 둬서 스프링 컨텍스트를 같이 쓴다.
 */
@SpringBootTest(properties = {
        "paddle.api-key=pdl_sdbx_apikey_test",
        "paddle.webhook-secret=" + PaymentApiTest.SECRET,
        "paddle.premium-pass.price-id=pri_premium",
        "paddle.attack-ticket.price-id=pri_attack",
})
@AutoConfigureMockMvc
@DisplayName("Paddle 웹훅 동시 처리")
class PaymentWebhookConcurrencyTest {

    private static final String WEBHOOK = "/api/v1/payments/paddle/webhook";
    private static final int THREADS = 4;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaddleClient paddleClient;

    private User buyer;
    private final List<Long> paymentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String name = "c" + UUID.randomUUID().toString().substring(0, 8);
        buyer = userRepository.save(User.createMember(name + "@test.com", "encoded", name));
    }

    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAllById(paymentIds);
        userRepository.deleteById(buyer.getId());
    }

    private String pending(PaymentProduct product) {
        String transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "");
        Payment payment = paymentRepository.save(Payment.pending(buyer, product, transactionId));
        paymentIds.add(payment.getId());
        return transactionId;
    }

    private static String completedEvent(String eventId, String transactionId) {
        return """
                {"event_id": "%s", "event_type": "transaction.completed",
                 "occurred_at": "2026-11-10T03:00:00.000000Z",
                 "data": {"id": "%s", "status": "completed", "currency_code": "USD",
                          "details": {"totals": {"grand_total": "500"}}}}
                """.formatted(eventId, transactionId);
    }

    private int sendWebhook(String body) throws Exception {
        long ts = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(PaymentApiTest.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String h1 = HexFormat.of().formatHex(mac.doFinal((ts + ":" + body).getBytes(StandardCharsets.UTF_8)));
        return mockMvc.perform(post(WEBHOOK)
                        .header("Paddle-Signature", "ts=" + ts + ";h1=" + h1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    /** 모든 작업을 한꺼번에 출발시키고 끝날 때까지 기다림 */
    private static void runTogether(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private User reloadBuyer() {
        return userRepository.findById(buyer.getId()).orElseThrow();
    }

    @Test
    @DisplayName("같은 결제 완료 웹훅이 동시에 여러 번 와도 공격권은 1개만 들어감")
    void sameWebhookConcurrently() throws Exception {
        String transactionId = pending(PaymentProduct.ATTACK_TICKET);
        String body = completedEvent("evt_" + transactionId, transactionId);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> sendWebhook(body));
        }
        runTogether(tasks);

        assertThat(reloadBuyer().getAttackTickets()).isEqualTo(1);
        assertThat(paymentRepository.findByPaddleTransactionId(transactionId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("같은 사람의 다른 공격권 결제 완료가 동시에 와도 수를 덮어쓰지 않음")
    void differentPaymentsConcurrently() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String transactionId = pending(PaymentProduct.ATTACK_TICKET);
            tasks.add(() -> sendWebhook(completedEvent("evt_" + transactionId, transactionId)));
        }
        runTogether(tasks);

        assertThat(reloadBuyer().getAttackTickets()).isEqualTo(THREADS);
    }

    @Test
    @DisplayName("프리미엄 결제창 두 개를 모두 결제하면 두 결제 모두 COMPLETED 로 남음 (두 번째는 환불 대상)")
    void duplicatePremiumPayments() throws Exception {
        String first = pending(PaymentProduct.PREMIUM_PASS);
        String second = pending(PaymentProduct.PREMIUM_PASS);

        sendWebhook(completedEvent("evt_" + first, first));
        sendWebhook(completedEvent("evt_" + second, second));

        assertThat(reloadBuyer().isPremium()).isTrue();
        assertThat(paymentRepository.findByPaddleTransactionId(first).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentRepository.findByPaddleTransactionId(second).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
    }
}
