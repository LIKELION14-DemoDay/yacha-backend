package likelion.yacha_backend.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import likelion.yacha_backend.domain.payment.client.PaddleClient;
import likelion.yacha_backend.domain.payment.entity.Payment;
import likelion.yacha_backend.domain.payment.entity.PaymentProduct;
import likelion.yacha_backend.domain.payment.entity.PaymentStatus;
import likelion.yacha_backend.domain.payment.repository.PaymentRepository;
import likelion.yacha_backend.domain.payment.service.PaymentErrorCode;
import likelion.yacha_backend.domain.payment.service.PaymentService;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 결제 조회 · 환불, 환불 웹훅. Paddle API 는 목, 웹훅은 실제 서명으로 보낸다.
 * 결제 완료까지는 웹훅으로 만들어 실제 흐름과 같게 둔다.
 */
@SpringBootTest(properties = {
        "paddle.api-key=pdl_sdbx_apikey_test",
        "paddle.webhook-secret=" + PaymentApiTest.SECRET,
        "paddle.premium-pass.price-id=pri_premium",
        "paddle.attack-ticket.price-id=pri_attack",
})
@AutoConfigureMockMvc
@Transactional
@DisplayName("관리자 결제 조회 · 환불")
class PaymentAdminApiTest {

    private static final String ADMIN = "/api/v1/admin/payments";
    private static final String WEBHOOK = "/api/v1/payments/paddle/webhook";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @MockitoBean
    private PaddleClient paddleClient;

    private User admin;
    private String adminToken;
    private User buyer;

    @BeforeEach
    void setUp() {
        admin = member();
        adminToken = "Bearer " + jwtTokenProvider.createAccessToken(admin.getId(), Role.ADMIN);
        buyer = member();
    }

    private User member() {
        String name = "p" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.createMember(name + "@test.com", "encoded", name));
    }

    /** 결제 요청 → 결제 완료 웹훅까지 마친 결제 */
    private Payment completedPayment(PaymentProduct product, String transactionId) throws Exception {
        given(paddleClient.createTransaction(anyString(), anyMap())).willReturn(transactionId);
        paymentService.checkout(buyer.getId(), product);
        sendWebhook(PaymentApiTest.completedEvent(transactionId)).andExpect(status().isOk());
        return paymentRepository.findByPaddleTransactionId(transactionId).orElseThrow();
    }

    private ResultActions sendWebhook(String body) throws Exception {
        long ts = Instant.now().getEpochSecond();
        return mockMvc.perform(post(WEBHOOK)
                .header("Paddle-Signature", "ts=" + ts + ";h1=" + PaymentApiTest.sign(ts, body))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String adjustmentEvent(String eventType, String eventId, String adjustmentId,
                                          String transactionId, String action, String status) {
        return """
                {"event_id": "%s", "event_type": "%s", "occurred_at": "2026-11-10T04:00:00.000000Z",
                 "data": {"id": "%s", "transaction_id": "%s", "action": "%s", "type": "full",
                          "status": "%s", "reason": "고객 요청", "currency_code": "USD"}}
                """.formatted(eventId, eventType, adjustmentId, transactionId, action, status);
    }

    private ResultActions refund(Long paymentId) throws Exception {
        return mockMvc.perform(post(ADMIN + "/" + paymentId + "/refund")
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"고객 요청\"}"));
    }

    private User reloadBuyer() {
        return userRepository.findById(buyer.getId()).orElseThrow();
    }

    // ---- 조회 ----

    @Test
    @DisplayName("회원 id · 이메일 · 거래 ID 로 결제를 찾고 금액 · 영수증 번호가 보임")
    void search() throws Exception {
        completedPayment(PaymentProduct.ATTACK_TICKET, "txn_search_1");
        completedPayment(PaymentProduct.PREMIUM_PASS, "txn_search_2");

        mockMvc.perform(get(ADMIN).param("userId", String.valueOf(buyer.getId()))
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                // 최근 결제부터
                .andExpect(jsonPath("$.data[0].transactionId").value("txn_search_2"))
                .andExpect(jsonPath("$.data[0].email").value(buyer.getEmail()))
                .andExpect(jsonPath("$.data[0].amount").value("500"))
                .andExpect(jsonPath("$.data[0].currencyCode").value("USD"))
                .andExpect(jsonPath("$.data[0].invoiceNumber").value("325-10001"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));

        mockMvc.perform(get(ADMIN).param("email", buyer.getEmail()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get(ADMIN).param("transactionId", "txn_search_1").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].product").value("ATTACK_TICKET"));
        mockMvc.perform(get(ADMIN).param("email", "nobody@test.com").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("조건 없이 찾으면 400")
    void searchRequiresCondition() throws Exception {
        mockMvc.perform(get(ADMIN).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_CONDITION_REQUIRED"));
    }

    @Test
    @DisplayName("관리자가 아니면 403")
    void adminOnly() throws Exception {
        String userToken = "Bearer " + jwtTokenProvider.createAccessToken(buyer.getId(), Role.USER);

        mockMvc.perform(get(ADMIN).param("userId", "1").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN + "/1/refund").header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("상세에 회원의 지금 혜택 · 환불 가능 여부 · 결제 로그가 나옴")
    void detail() throws Exception {
        Payment payment = completedPayment(PaymentProduct.ATTACK_TICKET, "txn_detail");

        mockMvc.perform(get(ADMIN + "/" + payment.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.data.userAttackTickets").value(1))
                .andExpect(jsonPath("$.data.refundable").value(true))
                .andExpect(jsonPath("$.data.refundBlockedCode").doesNotExist())
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].eventType").value("transaction.completed"))
                .andExpect(jsonPath("$.data.events[0].result").value("APPLIED"))
                .andExpect(jsonPath("$.data.events[0].payload").isNotEmpty());

        mockMvc.perform(get(ADMIN + "/999999").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    // ---- 환불 ----

    @Test
    @DisplayName("프리미엄 환불: 요청하면 바로 해제되고, 승인 웹훅이 오면 REFUNDED")
    void refundPremium() throws Exception {
        Payment payment = completedPayment(PaymentProduct.PREMIUM_PASS, "txn_refund_premium");
        given(paddleClient.createFullRefund("txn_refund_premium", "고객 요청"))
                .willReturn(new PaddleClient.RefundResult("adj_premium", "pending_approval"));

        refund(payment.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUND_PENDING"))
                .andExpect(jsonPath("$.data.paddleAdjustmentId").value("adj_premium"))
                .andExpect(jsonPath("$.data.refundReason").value("고객 요청"))
                .andExpect(jsonPath("$.data.refundRequestedBy").value(admin.getId()));
        assertThat(reloadBuyer().isPremium()).isFalse();

        sendWebhook(adjustmentEvent("adjustment.updated", "evt_adj_ok", "adj_premium",
                "txn_refund_premium", "refund", "approved")).andExpect(status().isOk());

        Payment refunded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.getRefundedAt()).isNotNull();
        assertThat(reloadBuyer().isPremium()).isFalse();
    }

    @Test
    @DisplayName("Paddle 이 바로 승인하면 요청 응답부터 REFUNDED")
    void refundApprovedImmediately() throws Exception {
        Payment payment = completedPayment(PaymentProduct.ATTACK_TICKET, "txn_refund_now");
        given(paddleClient.createFullRefund(eq("txn_refund_now"), anyString()))
                .willReturn(new PaddleClient.RefundResult("adj_now", "approved"));

        refund(payment.getId()).andExpect(jsonPath("$.data.status").value("REFUNDED"));
        assertThat(reloadBuyer().getAttackTickets()).isZero();

        // 뒤늦게 온 adjustment.created(approved) 는 이미 반영된 상태라 넘어감
        sendWebhook(adjustmentEvent("adjustment.created", "evt_adj_now", "adj_now",
                "txn_refund_now", "refund", "approved")).andExpect(status().isOk());
        assertThat(reloadBuyer().getAttackTickets()).isZero();
    }

    @Test
    @DisplayName("환불이 거절되면 회수한 혜택을 돌려주고 COMPLETED")
    void refundRejected() throws Exception {
        Payment payment = completedPayment(PaymentProduct.ATTACK_TICKET, "txn_refund_reject");
        given(paddleClient.createFullRefund(eq("txn_refund_reject"), anyString()))
                .willReturn(new PaddleClient.RefundResult("adj_reject", "pending_approval"));

        refund(payment.getId()).andExpect(status().isOk());
        assertThat(reloadBuyer().getAttackTickets()).isZero();

        sendWebhook(adjustmentEvent("adjustment.updated", "evt_adj_reject", "adj_reject",
                "txn_refund_reject", "refund", "rejected")).andExpect(status().isOk());

        Payment restored = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(restored.getRefundReason()).startsWith("[거절됨]");
        assertThat(reloadBuyer().getAttackTickets()).isEqualTo(1);
    }

    @Test
    @DisplayName("공격권을 이미 썼으면 환불 안 됨 (409), Paddle 도 부르지 않음")
    void attackTicketAlreadyUsed() throws Exception {
        Payment payment = completedPayment(PaymentProduct.ATTACK_TICKET, "txn_used");
        User user = reloadBuyer();
        user.removeAttackTicket();   // 채팅에서 공격권을 씀
        userRepository.flush();

        refund(payment.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ATTACK_TICKET_ALREADY_USED"));
        mockMvc.perform(get(ADMIN + "/" + payment.getId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.refundable").value(false))
                .andExpect(jsonPath("$.data.refundBlockedCode").value("ATTACK_TICKET_ALREADY_USED"));
        verify(paddleClient, never()).createFullRefund(anyString(), anyString());
    }

    @Test
    @DisplayName("결제 완료가 아니면(결제 전 · 환불 중) 환불 안 됨 (409)")
    void refundNotAllowed() throws Exception {
        given(paddleClient.createTransaction(anyString(), anyMap())).willReturn("txn_pending_only");
        paymentService.checkout(buyer.getId(), PaymentProduct.PREMIUM_PASS);
        Payment pending = paymentRepository.findByPaddleTransactionId("txn_pending_only").orElseThrow();

        refund(pending.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REFUND_NOT_ALLOWED"));

        Payment payment = completedPayment(PaymentProduct.ATTACK_TICKET, "txn_twice");
        given(paddleClient.createFullRefund(eq("txn_twice"), anyString()))
                .willReturn(new PaddleClient.RefundResult("adj_twice", "pending_approval"));
        refund(payment.getId()).andExpect(status().isOk());
        refund(payment.getId()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Paddle 환불 요청이 실패하면 502, 혜택 · 상태 그대로")
    void refundProviderFailureKeepsBenefit() throws Exception {
        Payment payment = completedPayment(PaymentProduct.PREMIUM_PASS, "txn_fail");
        given(paddleClient.createFullRefund(eq("txn_fail"), anyString()))
                .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR));

        refund(payment.getId())
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_PROVIDER_ERROR"));

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(reloadBuyer().isPremium()).isTrue();
    }

    @Test
    @DisplayName("대시보드 환불 · 차지백처럼 우리 API 밖의 환불이 승인돼도 혜택을 회수하고 REFUNDED")
    void externalRefund() throws Exception {
        Payment premium = completedPayment(PaymentProduct.PREMIUM_PASS, "txn_external");

        sendWebhook(adjustmentEvent("adjustment.created", "evt_chargeback", "adj_cb",
                "txn_external", "chargeback", "approved")).andExpect(status().isOk());

        Payment refunded = paymentRepository.findById(premium.getId()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.getPaddleAdjustmentId()).isEqualTo("adj_cb");
        assertThat(refunded.getRefundReason()).startsWith("[chargeback]");
        assertThat(refunded.getRefundRequestedBy()).isNull();
        assertThat(reloadBuyer().isPremium()).isFalse();
    }

    @Test
    @DisplayName("승인 대기 상태의 환불 이벤트는 기록만 하고 혜택은 그대로")
    void pendingAdjustmentIgnored() throws Exception {
        completedPayment(PaymentProduct.ATTACK_TICKET, "txn_wait");

        sendWebhook(adjustmentEvent("adjustment.created", "evt_wait", "adj_wait",
                "txn_wait", "refund", "pending_approval")).andExpect(status().isOk());

        assertThat(reloadBuyer().getAttackTickets()).isEqualTo(1);
        assertThat(paymentRepository.findByPaddleTransactionId("txn_wait").orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
    }
}
