package likelion.yacha_backend.domain.philosophy.exception;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PhilosophyErrorCode implements BaseErrorCode {

    /** 1분 철학은 월요일부터 한 주 단위 */
    INVALID_WEEK_START(HttpStatus.BAD_REQUEST, "주 시작일은 월요일이어야 합니다."),
    /** 그 주에 정해진 편수가 이미 다 있음. 글을 바꾸려면 다시 생성, 주제를 바꾸려면 빼고 다시 생성 */
    WEEK_ALREADY_FULL(HttpStatus.CONFLICT, "이 주의 1분 철학은 이미 다 만들어져 있습니다."),
    ;

    private final HttpStatus status;
    private final String message;

    PhilosophyErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
