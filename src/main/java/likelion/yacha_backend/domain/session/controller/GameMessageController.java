package likelion.yacha_backend.domain.session.controller;

import java.security.Principal;
import likelion.yacha_backend.domain.session.dto.ChatSendRequest;
import likelion.yacha_backend.domain.session.service.BotService;
import likelion.yacha_backend.domain.session.service.GameMessageService;
import likelion.yacha_backend.global.exception.BaseErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import likelion.yacha_backend.global.response.ApiResponse;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * 토론 중 채팅 (명세 2-4, STOMP).
 *
 * <p>처리 중 에러는 연결을 끊지 않고 보낸 사람의 {@code /user/queue/errors} 로 REST 와 같은 형식을 보냅니다.
 * 이 연결(브라우저 탭)에만 보내고 같은 사용자의 다른 연결에는 보내지 않습니다.
 *
 * <p><b>채팅 본문을 로그에 남기지 않습니다</b> (명세 1-5). 에러 로그에도 요청 본문을 넣지 않습니다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class GameMessageController {

    private static final String ERROR_QUEUE = "/queue/errors";

    private final GameMessageService gameMessageService;
    private final BotService botService;

    /** {@code SEND /app/sessions/{id}/chat} — {@code CHAT} 구간, 반론 공개(143초) 뒤부터, 참가자만. */
    @MessageMapping("/sessions/{sessionId}/chat")
    public void chat(@DestinationVariable Long sessionId, @Payload ChatSendRequest request, Principal principal) {
        gameMessageService.sendChat(sessionId, AuthUser.from(principal).getUserId(), request.content());
        // 봇전이면 마지막 말 3초 뒤 봇이 대답합니다. 채팅이 거부되면 위에서 예외가 나 여기까지 오지 않습니다.
        // 채팅은 이미 보냈으므로 대답 예약이 실패해도 에러로 응답하지 않고, 그 대답만 건너뜁니다.
        try {
            botService.humanSpoke(sessionId);
        } catch (RuntimeException e) {
            log.error("[봇] 대답 일정을 등록하지 못했습니다. sessionId={}", sessionId, e);
        }
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        BaseErrorCode errorCode = e.getErrorCode();
        log.info("[STOMP {}]", errorCode.name());
        return ApiResponse.error(errorCode, errorCode.getMessage());
    }

    /** 본문이 JSON 이 아니거나 형식이 맞지 않는 경우. 예외 메시지에 본문이 들어갈 수 있어 로그에 남기지 않습니다. */
    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    public ApiResponse<Void> handleConversionException(MessageConversionException e) {
        log.info("[STOMP VALIDATION_FAILED] 메시지 본문을 읽지 못했습니다");
        return ApiResponse.error(GlobalErrorCode.VALIDATION_FAILED, GlobalErrorCode.VALIDATION_FAILED.getMessage());
    }

    /** 예상하지 못한 오류. 원인은 남기되 클라이언트에는 내부 정보를 보내지 않습니다. */
    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("[STOMP INTERNAL_SERVER_ERROR] {}", e.getClass().getName(), e);
        return ApiResponse.error(GlobalErrorCode.INTERNAL_SERVER_ERROR,
                GlobalErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }
}
