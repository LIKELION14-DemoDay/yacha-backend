package likelion.yacha_backend.domain.session.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import likelion.yacha_backend.global.security.stomp.StompSubscriptionAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 토론방({@code /topic/sessions/{id}}) 구독 권한. 규칙은 {@link SessionAccessService} 와 같습니다.
 *
 * <p>simple broker 는 구독자 전원에게 그대로 보내므로 여기서 막지 못하면 채팅이 그대로 새어 나갑니다.
 * {@code /topic/sessions/} 아래는 이 구현이 모두 맡고, 형식이 맞지 않는 목적지({@code /topic/sessions/abc},
 * {@code /topic/sessions/1/x}, 와일드카드)는 거부합니다.
 */
@Component
@RequiredArgsConstructor
public class SessionTopicSubscriptionAuthorizer implements StompSubscriptionAuthorizer {

    private static final String SESSION_TOPIC_PREFIX = "/topic/sessions/";
    private static final Pattern SESSION_TOPIC = Pattern.compile("^/topic/sessions/(\\d{1,18})$");

    private final SessionAccessService sessionAccessService;

    /** 토론방 목적지. 채팅 · 구간 · 종료 이벤트를 이 목적지로 보냅니다. */
    public static String destinationOf(Long sessionId) {
        return SESSION_TOPIC_PREFIX + sessionId;
    }

    @Override
    public boolean supports(String destination) {
        return destination.startsWith(SESSION_TOPIC_PREFIX);
    }

    @Override
    public boolean canSubscribe(AuthUser user, String destination) {
        Matcher matcher = SESSION_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return false;
        }
        return sessionAccessService.canView(Long.parseLong(matcher.group(1)), user.getUserId());
    }
}
