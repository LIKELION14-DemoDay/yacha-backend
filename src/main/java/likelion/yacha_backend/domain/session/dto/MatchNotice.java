package likelion.yacha_backend.domain.session.dto;

/**
 * 방장 알림 ({@code /user/queue/match}, 명세 2-4).
 *
 * @param type      알림 종류
 * @param sessionId 방장의 방
 */
public record MatchNotice(Type type, Long sessionId) {

    public enum Type {
        /** 상대가 들어와 게임이 시작됨. 방장은 승낙 없이 자동 수락됩니다 */
        MATCHED,
    }

    public static MatchNotice matched(Long sessionId) {
        return new MatchNotice(Type.MATCHED, sessionId);
    }
}
