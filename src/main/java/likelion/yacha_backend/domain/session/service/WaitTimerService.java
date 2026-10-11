package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 랜덤 방의 대기 타이머 (명세 2-3-2). {@code created_at} 기준으로
 * <ul>
 *   <li>30초마다 방장에게 {@code WAIT_PROMPT} — "봇전으로 시작하시겠습니까?"</li>
 *   <li>5분이 되면 방을 취소({@code CANCELLED})하고 {@code WAIT_EXPIRED}</li>
 * </ul>
 * 방장이 떠난 방도 5분 뒤에는 정리되므로, 대기열에 버려진 방이 남지 않습니다.
 *
 * <p>입장 · 취소 · AI 전환이 성공하면 {@link #cancel} 로 남은 타이머를 지웁니다. 지우기 전에 이미 실행된 작업은
 * DB 상태를 다시 확인하므로 아무것도 하지 않습니다 — 팝업은 {@code WAITING} 일 때만 보내고, 취소는 조건부 UPDATE 입니다.
 *
 * <p>팝업은 세션 행을 잠근 채로 확인하고 보냅니다. 확인과 전송 사이에 입장 · AI 전환이 끼어들면
 * 이미 시작된 게임에 팝업이 뜨기 때문입니다. 잠금 동안 그 조건부 UPDATE 는 기다리므로, 팝업은 상태가 바뀌기 전에 나가고
 * {@code MATCHED} 는 그 뒤에 나갑니다 (연결별 전송 순서는 {@code setPreservePublishOrder} 가 지킵니다).
 *
 * <p>타이머는 {@link GameTimers} 에 세션 id 로 등록합니다. 매칭된 뒤의 구간 타이머도 같은 세션 id 를 쓰므로,
 * 만료 작업은 <b>방을 실제로 취소했을 때만</b> 타이머를 지웁니다.
 */
@Service
@EnableConfigurationProperties(WaitTimerProperties.class)
public class WaitTimerService {

    private final GameTimers gameTimers;
    private final DebateSessionRepository sessionRepository;
    private final MatchNotifier matchNotifier;
    private final TransactionTemplate transactionTemplate;
    private final WaitTimerProperties properties;
    private final Clock clock;

    public WaitTimerService(GameTimers gameTimers, DebateSessionRepository sessionRepository,
                            MatchNotifier matchNotifier, PlatformTransactionManager transactionManager,
                            WaitTimerProperties properties, Clock clock) {
        this.gameTimers = gameTimers;
        this.sessionRepository = sessionRepository;
        this.matchNotifier = matchNotifier;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 대기 타이머를 등록합니다. 방 생성 커밋 뒤, 그리고 서버 재시작 뒤 대기 방을 다시 등록할 때 부릅니다.
     * 이미 지난 팝업은 건너뜁니다 (재시작 직후 팝업이 몰려 나가지 않게). 상한이 이미 지났으면 바로 취소합니다.
     *
     * @param hostUserId 방장. 게스트 정리로 계정이 지워졌으면 null — 알림 없이 취소만 합니다
     */
    public void register(Long sessionId, Long hostUserId, LocalDateTime createdAt) {
        Duration interval = properties.promptInterval();
        LocalDateTime expiresAt = expiresAt(createdAt);
        LocalDateTime now = LocalDateTime.now(clock);
        for (LocalDateTime at = createdAt.plus(interval); at.isBefore(expiresAt); at = at.plus(interval)) {
            if (at.isBefore(now)) {
                continue;
            }
            long waitedSeconds = Duration.between(createdAt, at).toSeconds();
            gameTimers.schedule(sessionId, at, () -> prompt(sessionId, hostUserId, waitedSeconds, expiresAt));
        }
        gameTimers.schedule(sessionId, expiresAt, () -> expire(sessionId, hostUserId));
    }

    /** 대기 상한 시각. 이때 방을 취소합니다. {@code WAIT_PROMPT} 와 {@code GET /sessions/current} 가 같은 값을 줍니다. */
    public LocalDateTime expiresAt(LocalDateTime createdAt) {
        return createdAt.plus(properties.limit());
    }

    /** 입장 · 취소 · AI 전환으로 대기가 끝났습니다. 남은 대기 타이머를 지웁니다. */
    public void cancel(Long sessionId) {
        gameTimers.cancelAll(sessionId);
    }

    /**
     * 대기 팝업. 세션 행을 잠근 채로 {@code WAITING} 인지 확인하고 보내므로, 상태가 바뀐 뒤에는 나가지 않습니다.
     * 타이머가 늦게 돌아 상한이 지났으면 곧 취소될 방이라 보내지 않습니다.
     */
    void prompt(Long sessionId, Long hostUserId, long waitedSeconds, LocalDateTime expiresAt) {
        if (hostUserId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            boolean waiting = sessionRepository.findByIdForUpdate(sessionId)
                    .map(DebateSession::isWaiting).orElse(false);
            if (waiting && LocalDateTime.now(clock).isBefore(expiresAt)) {
                matchNotifier.waitPrompt(hostUserId, sessionId, waitedSeconds, expiresAt);
            }
        });
    }

    private void expire(Long sessionId, Long hostUserId) {
        Integer cancelled = transactionTemplate.execute(
                status -> sessionRepository.cancelIfWaiting(sessionId, LocalDateTime.now(clock)));
        if (cancelled == null || cancelled == 0) {
            // 그 사이 입장 · 취소 · AI 전환됨. 매칭됐다면 구간 타이머가 같은 세션 id 로 있을 수 있어 지우지 않습니다.
            return;
        }
        gameTimers.cancelAll(sessionId);
        if (hostUserId != null) {
            matchNotifier.waitExpired(hostUserId, sessionId);
        }
    }
}
