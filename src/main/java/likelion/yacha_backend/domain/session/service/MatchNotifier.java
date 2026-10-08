package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.session.dto.MatchNotice;
import likelion.yacha_backend.global.util.DateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 방장 알림을 본인에게만 보냅니다 ({@code /user/queue/match}, 명세 2-4).
 *
 * <p>STOMP 사용자 이름이 userId 라서({@code AuthUser#getUsername}) userId 로 보냅니다.
 * 방장이 연결돼 있지 않으면 알림은 사라집니다. 재접속하면 {@code GET /sessions/current} · {@code /state} 로 상태를 맞춥니다.
 */
@Component
@RequiredArgsConstructor
public class MatchNotifier {

    static final String MATCH_QUEUE = "/queue/match";

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public void matched(Long hostUserId, Long sessionId) {
        send(hostUserId, MatchNotice.matched(sessionId));
    }

    /** 대기 팝업. {@code expiresAt} 은 KST 시각이고 오프셋을 붙여 보냅니다. */
    public void waitPrompt(Long hostUserId, Long sessionId, long waitedSeconds, LocalDateTime expiresAt) {
        send(hostUserId, MatchNotice.waitPrompt(sessionId, waitedSeconds,
                DateTimes.withOffset(expiresAt, clock.getZone())));
    }

    public void waitExpired(Long hostUserId, Long sessionId) {
        send(hostUserId, MatchNotice.waitExpired(sessionId));
    }

    private void send(Long userId, MatchNotice notice) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), MATCH_QUEUE, notice);
    }
}
