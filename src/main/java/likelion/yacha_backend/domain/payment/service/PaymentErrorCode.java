package likelion.yacha_backend.domain.payment.service;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PaymentErrorCode implements BaseErrorCode {

    /** Paddle API 키나 상품 가격 ID 가 환경변수에 없음 */
    PAYMENT_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "결제 설정이 완료되지 않았습니다."),
    /** Paddle 이 거래 생성을 거부하거나 응답하지 않음 */
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "결제 서버와 통신하지 못했습니다."),
    /** 프리미엄 패스는 영구라 이미 가진 사람은 다시 살 수 없음 */
    ALREADY_PREMIUM(HttpStatus.CONFLICT, "이미 프리미엄 패스를 가지고 있습니다."),
    /** 웹훅 서명이 없거나 맞지 않음, 또는 너무 오래된 서명 */
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "웹훅 서명이 올바르지 않습니다."),
    /** 서명은 맞지만 본문을 읽을 수 없음 */
    INVALID_WEBHOOK_PAYLOAD(HttpStatus.BAD_REQUEST, "웹훅 본문을 읽을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String message;

    PaymentErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
