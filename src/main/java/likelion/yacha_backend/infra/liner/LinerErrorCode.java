package likelion.yacha_backend.infra.liner;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum LinerErrorCode implements BaseErrorCode {

    LINER_NOT_CONFIGURED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "LINER API 설정이 완료되지 않았습니다."
    ),

    LINER_INVALID_REQUEST(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "LINER API 요청 형식이 올바르지 않습니다."
    ),

    LINER_REQUEST_REJECTED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "LINER API 요청이 거부되었습니다."
    ),

    LINER_CREDIT_EXHAUSTED(
            HttpStatus.BAD_GATEWAY,
            "LINER API 크레딧이 부족합니다."
    ),

    LINER_RATE_LIMITED(
            HttpStatus.BAD_GATEWAY,
            "LINER API 요청이 일시적으로 제한되었습니다."
    ),

    LINER_PROVIDER_ERROR(
            HttpStatus.BAD_GATEWAY,
            "LINER 검색 서버와 통신하지 못했습니다."
    );

    private final HttpStatus status;
    private final String message;

    LinerErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}