package likelion.yacha_backend.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param reason 환불 사유. Paddle 대시보드에도 그대로 보임 */
public record RefundRequest(
        @NotBlank @Size(max = 200) String reason
) {
}
