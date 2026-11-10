package likelion.yacha_backend.domain.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import likelion.yacha_backend.domain.payment.dto.AdminPaymentDetailResponse;
import likelion.yacha_backend.domain.payment.dto.AdminPaymentResponse;
import likelion.yacha_backend.domain.payment.dto.RefundRequest;
import likelion.yacha_backend.domain.payment.service.PaymentAdminService;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SecurityConfig 의 /api/v1/admin/** 규칙으로 ADMIN JWT 만 허용된다. */
@Tag(name = "결제 관리", description = "관리자용 결제 조회 · 환불")
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentAdminService paymentAdminService;

    @Operation(
            summary = "결제 검색",
            description = """
                    `transactionId` · `userId` · `email` 중 하나로 찾음 (여러 개면 이 순서로 앞의 것 하나만 씀)
                    최근 결제부터. 결제창만 열고 끝난 PENDING 도 나옴
                    """)
    @GetMapping
    public ApiResponse<List<AdminPaymentResponse>> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String transactionId
    ) {
        return ApiResponse.success(paymentAdminService.search(userId, email, transactionId));
    }

    @Operation(
            summary = "결제 상세",
            description = "결제 · 회원의 지금 혜택 · 환불 가능 여부 · 결제 로그(Paddle 웹훅 원본, 받은 순)")
    @GetMapping("/{paymentId}")
    public ApiResponse<AdminPaymentDetailResponse> getDetail(@PathVariable Long paymentId) {
        return ApiResponse.success(paymentAdminService.getDetail(paymentId));
    }

    @Operation(
            summary = "환불",
            description = """
                    Paddle 에 전체 환불을 요청하고 혜택을 바로 회수함
                    - 프리미엄 패스: 바로 해제
                    - 공격권: 쓰기 전에만 환불. 남은 공격권이 없으면 409 `ATTACK_TICKET_ALREADY_USED`

                    Paddle 승인을 기다리면 `REFUND_PENDING`, 바로 승인되면 `REFUNDED`
                    승인 · 거절은 웹훅으로 반영 (거절되면 혜택을 돌려주고 `COMPLETED`)
                    결제 완료가 아니면(결제 전 · 환불 중 · 환불됨) 409 `REFUND_NOT_ALLOWED`
                    """)
    @PostMapping("/{paymentId}/refund")
    public ApiResponse<AdminPaymentResponse> refund(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequest request
    ) {
        return ApiResponse.success(paymentAdminService.refund(paymentId, authUser.getUserId(), request.reason()));
    }
}
