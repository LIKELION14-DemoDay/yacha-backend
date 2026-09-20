package likelion.yacha_backend.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 에러코드 enum이 구현하는 인터페이스.
 *
 * <p>에러코드 enum 파일 하나를 여럿이 같이 고치면 merge 충돌이 나므로,
 * 각자 담당 영역에서 이 인터페이스를 구현하는 enum을 따로 만듭니다.
 *
 * <p>예시:
 * <pre>{@code
 * public enum SessionErrorCode implements BaseErrorCode {
 *     SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "토론을 찾을 수 없습니다.");
 *
 *     private final HttpStatus status;
 *     private final String message;
 *     ...
 * }
 * }</pre>
 */
public interface BaseErrorCode {

    HttpStatus getStatus();

    String getMessage();

    /** enum이 구현하면 자동으로 제공됩니다. (Enum.name()) */
    String name();
}
