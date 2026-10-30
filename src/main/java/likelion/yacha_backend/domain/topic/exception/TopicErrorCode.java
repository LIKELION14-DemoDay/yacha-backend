package likelion.yacha_backend.domain.topic.exception;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
public enum TopicErrorCode implements BaseErrorCode {

    /** 주제 없음. 카테고리에 활성 주제가 하나도 없는 경우도 이 코드입니다. */
    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "주제를 찾을 수 없습니다."),
    /** 랜덤 주제 넘기기에서 카테고리의 주제를 모두 봄. 프론트는 본 주제 목록을 비우고 처음부터 다시 요청합니다. */
    ALL_TOPICS_SEEN(HttpStatus.NOT_FOUND, "이 카테고리의 주제를 모두 봤습니다."),
    ;

    private final HttpStatus status;
    private final String message;

    TopicErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
