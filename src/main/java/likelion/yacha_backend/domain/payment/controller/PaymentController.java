package likelion.yacha_backend.domain.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import likelion.yacha_backend.domain.payment.dto.CheckoutRequest;
import likelion.yacha_backend.domain.payment.dto.CheckoutResponse;
import likelion.yacha_backend.domain.payment.dto.PaymentStatusResponse;
import likelion.yacha_backend.domain.payment.service.PaddleWebhookService;
import likelion.yacha_backend.domain.payment.service.PaymentService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "Paddle 결제 — 프리미엄 패스 · 채팅 공격권")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaddleWebhookService paddleWebhookService;

    @Operation(
            summary = "결제 요청",
            description = """
                    회원 전용. Paddle 거래를 만들고 `transactionId` 를 돌려줌

                    프론트는 Paddle.js 로 결제창을 엶
                    `Paddle.Checkout.open({ transactionId })`

                    결제창의 성공 콜백으로 혜택을 판단하지 말고, 결제 뒤 `GET /payments/me` 로 확인
                    (혜택은 Paddle 웹훅이 와야 들어가서 몇 초 늦을 수 있음)

                    - `PREMIUM_PASS`: 게임 무제한 (영구). 이미 가졌으면 409 `ALREADY_PREMIUM`
                    - `ATTACK_TICKET`: 채팅 공격권 1개
                    """)
    @PostMapping("/checkout")
    public ApiResponse<CheckoutResponse> checkout(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CheckoutRequest request
    ) {
        return ApiResponse.success(paymentService.checkout(authUser.getUserId(), request.product()));
    }

    @Operation(summary = "내 결제 상태", description = "회원 전용. 프리미엄 패스 여부 · 남은 공격권 수")
    @GetMapping("/me")
    public ApiResponse<PaymentStatusResponse> getMyStatus(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(paymentService.getMyStatus(authUser.getUserId()));
    }

    /**
     * Paddle 이 부르는 웹훅. 로그인 없이 열어 두고 Paddle-Signature 서명으로 막는다.
     * 서명은 받은 본문 그대로 계산해야 해서 JSON 으로 읽지 않고 byte[] 로 받는다.
     */
    @Operation(summary = "Paddle 웹훅", description = "Paddle 전용. 프론트에서 부르지 않음")
    @PostMapping("/paddle/webhook")
    public ApiResponse<Void> paddleWebhook(
            @RequestHeader(value = "Paddle-Signature", required = false) String signature,
            @RequestBody byte[] body
    ) {
        paddleWebhookService.handle(signature, body);
        return ApiResponse.noContent();
    }
}
