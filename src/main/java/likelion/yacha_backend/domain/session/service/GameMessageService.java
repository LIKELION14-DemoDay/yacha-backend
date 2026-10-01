package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.session.dto.GameMessageResponse;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameMessage;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 채팅 · 최종변론을 게임 메모리에 기록하고 토론방에 브로드캐스트합니다 (명세 2-4).
 *
 * <p>DB 를 거치지 않습니다. 참가자 · 구간 · 글자 수 검사는 게임 객체가 하고, 채팅 내용은 메모리에만 남습니다.
 *
 * <p><b>기록 → 전송을 게임 락 안에서</b> 합니다. 락 밖에서 보내면 먼저 채번한 메시지가 나중에 전송될 수 있어,
 * 받는 쪽의 순서가 {@code seqNo} 와 어긋납니다. 기록한 뒤에 보내므로 브로드캐스트한 메시지는 반드시 게임 객체에
 * 있고, 재접속 보충({@code /messages})에서 빠지지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class GameMessageService {

    private final GameRegistry gameRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public void sendChat(Long sessionId, Long userId, String content) {
        Game game = findGame(sessionId);
        synchronized (game) {
            broadcast(sessionId, game.appendChat(userId, content, LocalDateTime.now(clock)));
        }
    }

    public void submitFinal(Long sessionId, Long userId, String content) {
        Game game = findGame(sessionId);
        synchronized (game) {
            broadcast(sessionId, game.submitFinal(userId, content, LocalDateTime.now(clock)));
        }
    }

    /**
     * 게임은 매칭이 성사될 때 만들어지고 끝나면 지워집니다. 없으면 시작 전 · 종료 · 서버 재시작 중 하나이므로
     * DB 를 보지 않고 {@code SESSION_NOT_IN_PROGRESS} 로 끝냅니다. 채팅마다 DB 를 조회하지 않기 위해서입니다.
     */
    private Game findGame(Long sessionId) {
        return gameRegistry.find(sessionId)
                .orElseThrow(() -> new BusinessException(SessionErrorCode.SESSION_NOT_IN_PROGRESS));
    }

    private void broadcast(Long sessionId, GameMessage message) {
        messagingTemplate.convertAndSend(SessionTopicSubscriptionAuthorizer.destinationOf(sessionId),
                GameMessageResponse.from(message, clock.getZone()));
    }
}
