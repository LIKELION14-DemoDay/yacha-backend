package likelion.yacha_backend.domain.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * 방장 알림 ({@code /user/queue/match}, 명세 2-4). 값이 없는 필드는 보내지 않습니다.
 *
 * @param type          알림 종류
 * @param sessionId     방장의 방
 * @param waitedSeconds {@code WAIT_PROMPT} 만 — 방을 만든 뒤 기다린 초 (30, 60, …)
 * @param expiresAt     {@code WAIT_PROMPT} 만 — 대기 상한 시각. 이때 방이 취소됩니다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchNotice(Type type, Long sessionId, Long waitedSeconds, OffsetDateTime expiresAt) {

    public enum Type {
        /** 상대가 들어와 게임이 시작됨. 방장은 승낙 없이 자동 수락됩니다 */
        MATCHED,
        /** 30초마다 — 프론트가 "봇전으로 시작하시겠습니까?" 를 띄움 */
        WAIT_PROMPT,
        /** 대기 상한(5분) 도달 — 방은 이미 취소됨 */
        WAIT_EXPIRED,
    }

    public static MatchNotice matched(Long sessionId) {
        return new MatchNotice(Type.MATCHED, sessionId, null, null);
    }

    public static MatchNotice waitPrompt(Long sessionId, long waitedSeconds, OffsetDateTime expiresAt) {
        return new MatchNotice(Type.WAIT_PROMPT, sessionId, waitedSeconds, expiresAt);
    }

    public static MatchNotice waitExpired(Long sessionId) {
        return new MatchNotice(Type.WAIT_EXPIRED, sessionId, null, null);
    }
}
