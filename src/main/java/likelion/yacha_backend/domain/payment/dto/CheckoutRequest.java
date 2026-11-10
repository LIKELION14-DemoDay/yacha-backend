package likelion.yacha_backend.domain.payment.dto;

import jakarta.validation.constraints.NotNull;
import likelion.yacha_backend.domain.payment.entity.PaymentProduct;

public record CheckoutRequest(
        @NotNull PaymentProduct product
) {
}
