package likelion.yacha_backend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 특정 도메인에 속하지 않는 공통 에러코드.
 * 도메인 전용 에러(예: SESSION_NOT_FOUND)는 여기에 추가하지 말고,
 * {@link BaseErrorCode} 를 구현하는 별도 enum을 만드세요.
 */
@Getter
public enum GlobalErrorCode implements BaseErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력 데이터 검증에 실패했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    /** 게스트(비회원)가 회원 전용 기능을 부름. 인가 단계(JwtAccessDeniedHandler)에서 내므로 공통 코드에 둠 */
    GUEST_NOT_ALLOWED(HttpStatus.FORBIDDEN, "회원만 이용할 수 있습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류가 발생했습니다."),
    ;

    private final HttpStatus status;
    private final String message;

    GlobalErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
