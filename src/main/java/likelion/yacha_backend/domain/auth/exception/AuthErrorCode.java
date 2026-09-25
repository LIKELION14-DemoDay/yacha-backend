package likelion.yacha_backend.domain.auth.exception;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
public enum AuthErrorCode implements BaseErrorCode {

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    GUEST_NOT_ALLOWED(HttpStatus.FORBIDDEN, "회원만 이용할 수 있습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 회원으로 전환된 계정입니다."),
    ;

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
