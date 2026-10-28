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
    /** 이번 주보다 앞선 주에는 새로 만들지 않음 */
    PAST_WEEK(HttpStatus.BAD_REQUEST, "지난 주의 1분 철학은 만들 수 없습니다."),
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "1분 철학 글을 찾을 수 없습니다."),
    /** 공개된 글은 메모 · 북마크가 달려 있을 수 있어 다시 생성하거나 뺄 수 없음 */
    ARTICLE_ALREADY_PUBLISHED(HttpStatus.CONFLICT, "이미 공개된 1분 철학 글은 다시 만들거나 뺄 수 없습니다."),
    /** 생성 · 다시 생성은 한 번에 하나만 돎 */
    GENERATION_IN_PROGRESS(HttpStatus.CONFLICT, "1분 철학 글을 만드는 중입니다. 끝난 뒤 다시 시도해 주세요."),
    ;

    private final HttpStatus status;
    private final String message;

    PhilosophyErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
