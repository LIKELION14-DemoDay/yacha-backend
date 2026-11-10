package likelion.yacha_backend.domain.heart.exception;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum HeartErrorCode implements BaseErrorCode {

    /** 남은 하트가 0개라 방을 만들거나 들어갈 수 없음. 하나 쓰면 15분마다 1개씩 다시 참 */
    HEART_EXHAUSTED(HttpStatus.CONFLICT, "하트가 없습니다. 하트가 충전되면 다시 시도해 주세요."),
    ;

    private final HttpStatus status;
    private final String message;

    HeartErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
