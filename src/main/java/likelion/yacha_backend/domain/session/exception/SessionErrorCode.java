package likelion.yacha_backend.domain.session.exception;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
public enum SessionErrorCode implements BaseErrorCode {

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "토론을 찾을 수 없습니다."),
    /** 명세의 SESSION_FINISHED 자리입니다. 끝난 게임에 채팅 · 최종변론을 보낸 경우도 이 코드입니다. */
    SESSION_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 종료되었거나 진행 중이 아닌 토론입니다."),
    /** 내 세션이 아님. 관전자가 채팅 · 최종변론을 보내거나 근거 · 요약을 조회할 때도 이 코드입니다. */
    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "토론 참가자만 할 수 있습니다."),
    /** 현재 구간에서 허용되지 않는 동작. 구간은 서버가 메시지를 받은 시각으로 판단합니다. */
    INVALID_PHASE(HttpStatus.CONFLICT, "지금은 할 수 없는 구간입니다."),
    CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "글자 수를 초과했습니다."),
    FINAL_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "최종변론은 한 번만 제출할 수 있습니다."),
    /** 게임 하나에 쌓을 수 있는 메시지 수를 넘음. 도배 제한이 정해지기 전까지 메모리를 지키는 안전장치입니다. */
    MESSAGE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "이 토론에서 보낼 수 있는 메시지 수를 넘었습니다."),
    ;

    private final HttpStatus status;
    private final String message;

    SessionErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
