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
    INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해 주세요."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다."),
    SOCIAL_EMAIL_CONFLICT(HttpStatus.CONFLICT, "다른 방법으로 가입된 이메일입니다. 기존 로그인 방법을 이용해 주세요."),
    SOCIAL_LOGIN_RETRY(HttpStatus.CONFLICT, "로그인 처리가 겹쳤습니다. 다시 시도해 주세요."),
    /** 카카오 인가 코드가 만료됐거나(10분) 이미 쓰였거나, redirectUri 가 인가 요청 때와 다름 */
    INVALID_SOCIAL_CODE(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해 주세요."),
    /** 카카오 서버 오류 · 타임아웃, 또는 우리 쪽 키 설정 오류. 사용자가 다시 해도 해결되지 않을 수 있다 */
    SOCIAL_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "소셜 로그인 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 회원으로 전환된 계정입니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다."),
    PASSWORD_NOT_SET(HttpStatus.CONFLICT, "비밀번호로 로그인하는 계정이 아닙니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 비밀번호를 입력해 주세요."),
    ;

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
