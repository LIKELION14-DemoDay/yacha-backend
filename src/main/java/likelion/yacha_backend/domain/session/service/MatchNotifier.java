package likelion.yacha_backend.domain.session.service;

import likelion.yacha_backend.domain.session.dto.MatchNotice;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 방장 알림을 본인에게만 보냅니다 ({@code /user/queue/match}, 명세 2-4).
 *
 * <p>STOMP 사용자 이름이 userId 라서({@code AuthUser#getUsername}) userId 로 보냅니다.
 * 방장이 연결돼 있지 않으면 알림은 사라집니다. 재접속하면 {@code GET /sessions/{id}/state} 로 상태를 맞춥니다.
 */
@Component
@RequiredArgsConstructor
public class MatchNotifier {

    static final String MATCH_QUEUE = "/queue/match";

    private final SimpMessagingTemplate messagingTemplate;

    public void matched(Long hostUserId, Long sessionId) {
        messagingTemplate.convertAndSendToUser(String.valueOf(hostUserId), MATCH_QUEUE, MatchNotice.matched(sessionId));
    }
}
