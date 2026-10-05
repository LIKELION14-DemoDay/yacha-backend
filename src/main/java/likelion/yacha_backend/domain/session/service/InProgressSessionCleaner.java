package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 서버가 뜰 때 진행 중이던 게임을 무효로 정리합니다 (명세 1-5 · 2-11).
 *
 * <p>채팅 · 주장은 서버 메모리에만 있어 재시작하면 사라지므로 게임을 이어갈 수 없습니다.
 * 그래서 {@code IN_PROGRESS} 세션을 모두 {@code FINISHED} + {@code finish_reason = ABORTED} 로 바꾸고,
 * 승패({@code result})는 NULL 로 둡니다.
 *
 * <p>세션마다 {@code finishIfInProgress} 조건부 UPDATE 로 끝냅니다. 정리하는 사이 다른 쪽이 먼저 끝낸 세션은
 * 0 이 돌아와 건너뜁니다. {@code WAITING} 방은 건드리지 않습니다 (대기 타이머 재등록은 매칭 작업에서 합니다).
 *
 * <p>{@link ApplicationReadyEvent} 는 웹 서버가 요청을 받기 시작한 뒤에 옵니다. 그 사이 매칭돼 시작된 게임은
 * 이 서버 메모리에 있으므로, <b>이 서버가 뜨기 전에 시작된 게임만</b> 끝냅니다 (빈을 만들 때 시각 기준).
 *
 * <p>정리에 실패해도 서버 기동은 막지 않습니다. 리스너 예외는 기동 실패가 되므로 잡아서 로그만 남기고,
 * 한 세션이 실패해도 나머지 세션은 계속 정리합니다. 남은 세션은 다음 재시작 때 다시 정리됩니다.
 *
 * <p>서버 1대 전제입니다. 여러 대로 늘리면 다른 서버가 진행 중인 게임까지 끝내게 되므로 그때 다시 정합니다.
 * 무중단 배포(새 컨테이너를 먼저 띄우고 기존 컨테이너를 내리는 방식)로 바꿀 때도 같습니다. 새 서버가 기존 서버에서
 * 진행 중인 게임을 끝내게 되므로 정리 기준을 다시 정해야 합니다. 지금은 {@code docker-compose.prod.yml} 이
 * 기존 컨테이너를 내린 뒤 새 컨테이너를 띄웁니다.
 */
@Slf4j
@Service
public class InProgressSessionCleaner {

    private final DebateSessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    /** 이 서버가 뜬 시각. 이보다 먼저 시작된 게임만 이전 서버의 게임입니다. */
    private final LocalDateTime bootTime;

    public InProgressSessionCleaner(DebateSessionRepository sessionRepository,
                                    PlatformTransactionManager transactionManager, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.bootTime = LocalDateTime.now(clock);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            int aborted = abortInProgressSessions(bootTime);
            if (aborted > 0) {
                log.warn("[재시작 정리] 진행 중이던 게임 {}건을 ABORTED 로 정리했습니다.", aborted);
            }
        } catch (RuntimeException e) {
            log.error("[재시작 정리] 진행 중인 게임을 조회하지 못했습니다.", e);
        }
    }

    /**
     * {@code startedBefore} 보다 먼저 시작된 진행 중 세션을 ABORTED 로 끝내고, 실제로 끝낸 수를 돌려줍니다.
     * 세션 하나가 실패하면 로그만 남기고 다음 세션으로 넘어갑니다.
     */
    public int abortInProgressSessions(LocalDateTime startedBefore) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> sessionIds = sessionRepository
                .findAllByStatusAndStartedAtBefore(SessionStatus.IN_PROGRESS, startedBefore).stream()
                .map(DebateSession::getId)
                .toList();
        int aborted = 0;
        for (Long sessionId : sessionIds) {
            try {
                Integer updated = transactionTemplate.execute(
                        status -> sessionRepository.finishIfInProgress(sessionId, FinishReason.ABORTED, now));
                if (updated != null && updated == 1) {
                    aborted++;
                }
            } catch (RuntimeException e) {
                log.error("[재시작 정리] 세션을 정리하지 못했습니다. sessionId={}", sessionId, e);
            }
        }
        return aborted;
    }
}
