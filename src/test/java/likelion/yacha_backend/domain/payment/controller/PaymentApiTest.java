package likelion.yacha_backend.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import likelion.yacha_backend.domain.payment.client.PaddleClient;
import likelion.yacha_backend.domain.payment.entity.PaymentStatus;
import likelion.yacha_backend.domain.payment.repository.PaymentRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 요청 → Paddle 웹훅 → 혜택 반영까지. Paddle API 는 목으로 바꾸고, 웹훅은 실제 서명을 만들어 보낸다.
 */
@SpringBootTest(properties = {
        "paddle.api-key=pdl_sdbx_apikey_test",
        "paddle.webhook-secret=" + PaymentApiTest.SECRET,
        "paddle.premium-pass.price-id=pri_premium",
        "paddle.attack-ticket.price-id=pri_attack",
})
@AutoConfigureMockMvc
@Transactional
@DisplayName("결제 API · Paddle 웹훅")
class PaymentApiTest {

    static final String SECRET = "pdl_ntfset_api_test";
    private static final String WEBHOOK = "/api/v1/payments/paddle/webhook";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaddleClient paddleClient;

    private String member;

    @BeforeEach
    void signUp() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "buyer@example.com", "password": "password123", "nickname": "구매자"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        member = "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String checkout(String product, String transactionId) throws Exception {
        given(paddleClient.createTransaction(eq("PREMIUM_PASS".equals(product) ? "pri_premium" : "pri_attack"), anyMap()))
                .willReturn(transactionId);
        MvcResult result = mockMvc.perform(post("/api/v1/payments/checkout")
                        .header(HttpHeaders.AUTHORIZATION, member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\": \"" + product + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").isNumber())
                .andExpect(jsonPath("$.data.transactionId").value(transactionId))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.transactionId");
    }

    private static String completedEvent(String transactionId) {
        return """
                {"event_id": "evt_%s", "event_type": "transaction.completed",
                 "occurred_at": "2026-11-10T03:00:00.000000Z",
                 "data": {"id": "%s", "status": "completed", "custom_data": {"user_id": "1"}}}
                """.formatted(transactionId, transactionId);
    }

    private ResultActions sendWebhook(String body) throws Exception {
        long ts = Instant.now().getEpochSecond();
        return mockMvc.perform(post(WEBHOOK)
                .header("Paddle-Signature", "ts=" + ts + ";h1=" + sign(ts, body))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String sign(long ts, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((ts + ":" + body).getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions myStatus() throws Exception {
        return mockMvc.perform(get("/api/v1/payments/me").header(HttpHeaders.AUTHORIZATION, member));
    }

    @Test
    @DisplayName("결제 요청만 하면 PENDING 이고 혜택은 없음")
    void checkoutCreatesPending() throws Exception {
        String transactionId = checkout("PREMIUM_PASS", "txn_pending");

        assertThat(paymentRepository.findByPaddleTransactionId(transactionId))
                .get().extracting("status").isEqualTo(PaymentStatus.PENDING);
        myStatus().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.premium").value(false))
                .andExpect(jsonPath("$.data.attackTickets").value(0));
    }

    @Test
    @DisplayName("프리미엄 결제 완료 웹훅이 오면 프리미엄이 됨")
    void premiumCompleted() throws Exception {
        String transactionId = checkout("PREMIUM_PASS", "txn_premium");

        sendWebhook(completedEvent(transactionId)).andExpect(status().isOk());

        assertThat(paymentRepository.findByPaddleTransactionId(transactionId))
                .get().extracting("status").isEqualTo(PaymentStatus.COMPLETED);
        myStatus().andExpect(jsonPath("$.data.premium").value(true));
    }

    @Test
    @DisplayName("이미 프리미엄이면 프리미엄 패스를 다시 살 수 없음 (409)")
    void alreadyPremium() throws Exception {
        sendWebhook(completedEvent(checkout("PREMIUM_PASS", "txn_first"))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/checkout")
                        .header(HttpHeaders.AUTHORIZATION, member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\": \"PREMIUM_PASS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_PREMIUM"));
    }

    @Test
    @DisplayName("공격권은 결제마다 1개씩 쌓임")
    void attackTicketsAccumulate() throws Exception {
        sendWebhook(completedEvent(checkout("ATTACK_TICKET", "txn_attack_1"))).andExpect(status().isOk());
        sendWebhook(completedEvent(checkout("ATTACK_TICKET", "txn_attack_2"))).andExpect(status().isOk());

        myStatus().andExpect(jsonPath("$.data.attackTickets").value(2));
    }

    @Test
    @DisplayName("공격권 결제 완료 웹훅이 다시 와도 1개만 더해짐")
    void attackTicketCompletedOnlyOnce() throws Exception {
        String transactionId = checkout("ATTACK_TICKET", "txn_attack");
        String body = completedEvent(transactionId);

        sendWebhook(body).andExpect(status().isOk());
        sendWebhook(body).andExpect(status().isOk());

        myStatus().andExpect(jsonPath("$.data.attackTickets").value(1))
                .andExpect(jsonPath("$.data.premium").value(false));
    }

    @Test
    @DisplayName("서명이 틀리면 401 이고 혜택 없음")
    void invalidSignature() throws Exception {
        String transactionId = checkout("ATTACK_TICKET", "txn_forged");

        mockMvc.perform(post(WEBHOOK)
                        .header("Paddle-Signature", "ts=" + Instant.now().getEpochSecond() + ";h1=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedEvent(transactionId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_WEBHOOK_SIGNATURE"));

        myStatus().andExpect(jsonPath("$.data.attackTickets").value(0));
    }

    @Test
    @DisplayName("처리하지 않는 이벤트 · 모르는 거래는 200 으로 받고 넘어감 (Paddle 이 재전송하지 않게)")
    void ignoredEvents() throws Exception {
        sendWebhook("""
                {"event_id": "evt_x", "event_type": "transaction.created", "data": {"id": "txn_x"}}
                """).andExpect(status().isOk());
        sendWebhook(completedEvent("txn_unknown")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("게스트는 결제할 수 없음 (403)")
    void guestCannotCheckout() throws Exception {
        MvcResult guest = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();
        String guestBearer = "Bearer " + JsonPath.read(guest.getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(post("/api/v1/payments/checkout")
                        .header(HttpHeaders.AUTHORIZATION, guestBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\": \"PREMIUM_PASS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("상품이 없거나 모르는 값이면 400")
    void invalidProduct() throws Exception {
        mockMvc.perform(post("/api/v1/payments/checkout")
                        .header(HttpHeaders.AUTHORIZATION, member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/payments/checkout")
                        .header(HttpHeaders.AUTHORIZATION, member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\": \"GOLD\"}"))
                .andExpect(status().isBadRequest());
    }
}
