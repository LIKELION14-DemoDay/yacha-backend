package likelion.yacha_backend.domain.auth.exception;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
public enum AuthErrorCode implements BaseErrorCode {

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    /** 소셜 id_token 의 서명 · 발급자 · 대상 · 만료 중 하나라도 어긋남. 원인은 구분하지 않는다 */
    INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해 주세요."),
    /** 지원하지 않거나 서버에 설정되지 않은 소셜 공급자 */
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다."),
    /** 같은 이메일이 이미 다른 소셜 계정에 연결돼 있음. 원래 쓰던 방법으로 로그인해야 한다 */
    SOCIAL_EMAIL_CONFLICT(HttpStatus.CONFLICT, "다른 방법으로 가입된 이메일입니다. 기존 로그인 방법을 이용해 주세요."),
    /** 같은 소셜 계정으로 동시에 두 번 요청이 들어와 한쪽이 밀림. 다시 호출하면 정상 로그인된다 */
    SOCIAL_LOGIN_RETRY(HttpStatus.CONFLICT, "로그인 처리가 겹쳤습니다. 다시 시도해 주세요."),
    INVALID_SOCIAL_CODE(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해 주세요."),
    SOCIAL_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "소셜 로그인 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 회원으로 전환된 계정입니다."),
    /** 재설정 토큰이 없거나 만료(10분)됐거나 이미 사용됨. 원인은 구분하지 않는다 */
    INVALID_RESET_TOKEN(HttpStatus.UNAUTHORIZED, "인증이 만료됐거나 이미 사용됐습니다. 처음부터 다시 진행해 주세요."),
    /** 인증번호가 틀림. 남은 횟수 안에서 다시 입력할 수 있다 */
    INVALID_RESET_CODE(HttpStatus.BAD_REQUEST, "인증번호가 올바르지 않습니다."),
    /** 인증번호가 없음. 만료(3분) · 5번 틀림 · 요청한 적 없음 · 이미 사용함. 원인은 구분하지 않는다 */
    RESET_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 만료됐습니다. 다시 요청해 주세요."),
    /** 이메일 하나가 24시간 동안 인증번호를 10번 틀림. 가입 여부와 관계없이 같게 적용한다 */
    RESET_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 너무 많이 틀렸습니다. 24시간 뒤에 다시 시도해 주세요."),
    /** 비밀번호 변경에서 현재 비밀번호가 틀림. 이미 인증된 본인이라 원인을 알려줘도 된다 */
    CURRENT_PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다."),
    /** 비밀번호가 없는 계정(소셜 전용 · 게스트)이라 변경할 대상이 없음 */
    PASSWORD_NOT_SET(HttpStatus.CONFLICT, "비밀번호로 로그인하는 계정이 아닙니다."),
    /** 새 비밀번호가 현재와 같음. 바뀐 줄 알고 넘어가지 않도록 알려준다 */
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 비밀번호를 입력해 주세요."),
    ;

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
