package likelion.yacha_backend.domain.topic.exception;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
public enum TopicErrorCode implements BaseErrorCode {

    /** 주제 없음. 카테고리에 뽑을 주제가 없는 경우(방금 본 주제를 빼면 남는 게 없는 경우 포함)도 이 코드입니다. */
    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "주제를 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String message;

    TopicErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
