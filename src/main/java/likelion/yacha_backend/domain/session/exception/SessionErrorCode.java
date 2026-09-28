package likelion.yacha_backend.domain.session.exception;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
public enum SessionErrorCode implements BaseErrorCode {

    SESSION_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 종료되었거나 진행 중이 아닌 토론입니다."),
    ;

    private final HttpStatus status;
    private final String message;

    SessionErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
