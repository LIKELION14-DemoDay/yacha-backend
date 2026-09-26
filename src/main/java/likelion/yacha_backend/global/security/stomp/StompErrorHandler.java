package likelion.yacha_backend.global.security.stomp;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import likelion.yacha_backend.global.exception.BaseErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * 클라이언트 프레임 처리 중 난 예외를 STOMP ERROR 프레임으로 바꿉니다.
 *
 * <p>{@code message} 헤더에는 에러 코드(예: {@code UNAUTHORIZED})를, 본문에는 사용자에게 보여줄 메시지를
 * 담습니다. 기본 구현은 예외 메시지를 그대로 내보내 내부 채널 이름 같은 정보가 새므로 덮어씁니다.
 * ERROR 프레임을 보낸 뒤 서버는 연결을 닫습니다.
 */
@Slf4j
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        BaseErrorCode errorCode = findBusinessException(ex)
                .map(BusinessException::getErrorCode)
                .orElse(GlobalErrorCode.INTERNAL_SERVER_ERROR);

        if (errorCode == GlobalErrorCode.INTERNAL_SERVER_ERROR) {
            log.error("STOMP 프레임 처리 중 예상하지 못한 오류", ex);
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.name());
        accessor.setLeaveMutable(true);

        StompHeaderAccessor clientAccessor = clientMessage == null
                ? null
                : StompHeaderAccessor.wrap(clientMessage);
        if (clientAccessor != null && clientAccessor.getReceipt() != null) {
            accessor.setReceiptId(clientAccessor.getReceipt());
        }

        byte[] payload = errorCode.getMessage().getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    /** 채널이 인터셉터 예외를 MessageDeliveryException 으로 감싸므로 원인을 따라 내려갑니다. */
    private Optional<BusinessException> findBusinessException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof BusinessException businessException) {
                return Optional.of(businessException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
