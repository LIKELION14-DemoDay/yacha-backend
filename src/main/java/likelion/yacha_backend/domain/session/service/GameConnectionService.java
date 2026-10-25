package likelion.yacha_backend.domain.session.service;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import likelion.yacha_backend.domain.session.dto.OpponentDisconnectedEvent;
import likelion.yacha_backend.domain.session.dto.OpponentReconnectedEvent;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import likelion.yacha_backend.global.util.DateTimes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * 게임 중 연결 끊김 · 복귀 (명세 2-11).
 *
 * <pre>
 * 토론방 구독 ──▶ 이 연결을 그 참가자의 연결로 인정 (유예 중이었으면 복귀 → OPPONENT_RECONNECTED)
 * 연결 끊김   ──▶ 인정한 연결이면 disconnected_at 기록 · OPPONENT_DISCONNECTED · 유예 타이머
 * 유예 경과   ──▶ 아직 안 돌아왔으면 몰수패, 상대도 끊겨 있으면 무효 (GameExitService)
 * </pre>
 *
 * <p><b>복귀는 CONNECT 가 아니라 토론방을 다시 구독할 때</b>로 봅니다. 연결만 하고 게임 화면에 돌아오지 않은 경우를
 * 걸러냅니다. 게임 화면으로 다시 들어오는 경로는 없으므로(나가면 홈으로), 유예는 같은 화면에서 소켓이 자동으로
 * 다시 붙는 짧은 끊김만 살립니다.
 *
 * <p><b>최신 연결만 인정</b> — 새로고침처럼 새 연결이 먼저 구독하고 예전 연결이 늦게 끊기면 그 끊김은 무시합니다
 * ({@link Game#disconnect}). 구독 이벤트는 구독 권한 검사({@code StompAuthChannelInterceptor})를 통과한 뒤에만 옵니다.
 *
 * <p><b>게임이 생기기 전의 구독</b> — 토론방은 대기 중에도 구독할 수 있지만, 그때는 게임이 없어 연결을 인정하지
 * 못합니다. 그 구독을 유지한 채 매칭되면 다시 구독할 일이 없으므로, 게임을 만들 때 이미 구독 중인 연결을
 * 찾아 인정합니다 ({@link #connectSubscribed}).
 *
 * <p>유예 타이머는 따로 취소하지 않습니다. 실행될 때 "그 끊김 뒤로 아직 안 돌아왔는지" 를 게임에서 다시 확인하고,
 * 게임이 끝나면 {@link GameTimers#cancelAll} 로 같이 지워집니다. 봇은 연결이 없으므로 사람만 감지합니다.
 */
@Slf4j
@Service
@EnableConfigurationProperties(DisconnectProperties.class)
public class GameConnectionService {

    private static final Pattern SESSION_TOPIC = Pattern.compile("^/topic/sessions/(\\d{1,18})$");

    private final GameRegistry gameRegistry;
    private final GameTimers gameTimers;
    private final GameMessageService gameMessageService;
    private final GameExitService gameExitService;
    private final DebateParticipantRepository participantRepository;
    private final DisconnectProperties properties;
    private final SimpUserRegistry simpUserRegistry;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 연결(STOMP 세션 id) → 그 연결이 구독한 게임. 연결이 끊기면 여기서 찾아 처리하고 지웁니다. */
    private final Map<String, Set<Long>> sessionIdsByConnection = new ConcurrentHashMap<>();

    public GameConnectionService(GameRegistry gameRegistry, GameTimers gameTimers,
                                 GameMessageService gameMessageService, GameExitService gameExitService,
                                 DebateParticipantRepository participantRepository, DisconnectProperties properties,
                                 SimpUserRegistry simpUserRegistry, PlatformTransactionManager transactionManager,
                                 Clock clock) {
        this.gameRegistry = gameRegistry;
        this.gameTimers = gameTimers;
        this.gameMessageService = gameMessageService;
        this.gameExitService = gameExitService;
        this.participantRepository = participantRepository;
        this.properties = properties;
        this.simpUserRegistry = simpUserRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = userIdOf(event.getUser());
        if (userId == null || accessor.getSessionId() == null || accessor.getDestination() == null) {
            return;
        }
        Matcher matcher = SESSION_TOPIC.matcher(accessor.getDestination());
        if (matcher.matches()) {
            subscribed(Long.parseLong(matcher.group(1)), userId, accessor.getSessionId());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = userIdOf(event.getUser());
        Set<Long> sessionIds = sessionIdsByConnection.remove(event.getSessionId());
        if (userId == null || sessionIds == null) {
            return;
        }
        sessionIds.forEach(sessionId -> disconnected(sessionId, userId, event.getSessionId()));
    }

    /**
     * 게임을 막 만들었습니다. 그 전부터(대기 중에) 토론방을 구독하고 있던 참가자 연결을 인정합니다.
     * 구독 이벤트는 게임이 있을 때만 연결을 인정해서, 이게 없으면 그 사람이 끊겨도 감지하지 못합니다.
     */
    public void connectSubscribed(Long sessionId) {
        String destination = "/topic/sessions/" + sessionId;
        for (SimpSubscription subscription
                : simpUserRegistry.findSubscriptions(candidate -> destination.equals(candidate.getDestination()))) {
            Long userId = userIdOf(subscription.getSession().getUser().getPrincipal());
            if (userId != null) {
                subscribed(sessionId, userId, subscription.getSession().getId());
            }
        }
    }

    /** 참가자가 토론방을 구독했습니다. 관전자 · 끝난 게임이면 아무것도 하지 않습니다. */
    void subscribed(Long sessionId, Long userId, String connectionId) {
        Game game = gameRegistry.find(sessionId).orElse(null);
        if (game == null || !game.isParticipant(userId)) {
            return;
        }
        sessionIdsByConnection.computeIfAbsent(connectionId, id -> ConcurrentHashMap.newKeySet()).add(sessionId);
        boolean reconnected;
        synchronized (game) {
            reconnected = game.connect(userId, connectionId);
            if (reconnected) {
                gameMessageService.broadcastEvent(sessionId, OpponentReconnectedEvent.of());
            }
        }
        if (reconnected) {
            // 복귀는 메모리 상태로 이미 끝났습니다. 기록이 실패해도 로그만 남깁니다 (끊김 기록과 같은 방식).
            Long participantId = game.participantIdOf(userId);
            try {
                transactionTemplate.executeWithoutResult(status -> participantRepository.findById(participantId)
                        .ifPresent(participant -> participant.markReconnected()));
            } catch (RuntimeException e) {
                log.error("[재접속] disconnected_at 을 지우지 못했습니다. sessionId={}, participantId={}",
                        sessionId, participantId, e);
            }
        }
    }

    /** 연결이 끊겼습니다. 그 참가자가 지금 인정하는 연결일 때만 이탈 유예를 시작합니다. */
    void disconnected(Long sessionId, Long userId, String connectionId) {
        Game game = gameRegistry.find(sessionId).orElse(null);
        if (game == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime graceEndsAt = now.plus(properties.grace());
        OptionalLong disconnectNo;
        synchronized (game) {
            disconnectNo = game.disconnect(userId, connectionId);
            if (disconnectNo.isPresent()) {
                gameMessageService.broadcastEvent(sessionId,
                        OpponentDisconnectedEvent.of(DateTimes.withOffset(graceEndsAt, clock.getZone())));
            }
        }
        if (disconnectNo.isEmpty()) {
            return;
        }
        // 유예 타이머부터 겁니다. 기록이 실패해도 몰수패는 이어집니다.
        long no = disconnectNo.getAsLong();
        gameTimers.schedule(sessionId, graceEndsAt, () -> graceExpired(sessionId, userId, no));
        Long participantId = game.participantIdOf(userId);
        try {
            transactionTemplate.executeWithoutResult(status -> participantRepository.findById(participantId)
                    .ifPresent(participant -> participant.markDisconnected(now)));
        } catch (RuntimeException e) {
            log.error("[연결 끊김] disconnected_at 을 기록하지 못했습니다. sessionId={}, participantId={}",
                    sessionId, participantId, e);
        }
    }

    /**
     * 유예가 끝났습니다. 그 끊김 뒤로 돌아오지 않았으면 몰수패, 상대도 끊겨 있으면 무효로 끝냅니다.
     *
     * <p>확인과 종료를 게임 락 안에서 해서, 그 사이에 돌아온 구독이 끼어들지 못하게 합니다. 복귀 처리는 락을
     * 기다렸다가 끝난 게임을 보고 아무것도 하지 않습니다.
     */
    private void graceExpired(Long sessionId, Long userId, long disconnectNo) {
        Game game = gameRegistry.find(sessionId).orElse(null);
        if (game == null) {
            return;
        }
        synchronized (game) {
            if (game.isFinished() || !game.isStillDisconnected(userId, disconnectNo)) {
                return;
            }
            if (game.isOtherDisconnected(userId)) {
                gameExitService.abort(sessionId);
            } else {
                gameExitService.forfeit(sessionId, game.participantIdOf(userId));
            }
        }
    }

    /** 인증된 연결의 사용자 id. 인증 전에 끊긴 연결이면 null 입니다 (리스너는 예외를 던지지 않습니다). */
    private static Long userIdOf(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getUserId();
        }
        return null;
    }
}
