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
 * <p>채팅 · 주장 · 근거는 서버 메모리에만 있어 재시작하면 사라지므로 게임을 이어갈 수 없습니다.
 * 그래서 {@code IN_PROGRESS} 세션을 모두 {@code FINISHED} + {@code finish_reason = ABORTED} 로 바꾸고,
 * 승패({@code result})는 NULL 로 둡니다.
 *
 * <p>세션마다 {@code finishIfInProgress} 조건부 UPDATE 로 끝냅니다. 정리하는 사이 다른 쪽이 먼저 끝낸 세션은
 * 0 이 돌아와 건너뜁니다. {@code WAITING} 방은 건드리지 않습니다 (대기 타이머 재등록은 매칭 작업에서 합니다).
 *
 * <p>서버 1대 전제입니다. 여러 대로 늘리면 다른 서버가 진행 중인 게임까지 끝내게 되므로 그때 다시 정합니다.
 */
@Slf4j
@Service
public class InProgressSessionCleaner {

    private final DebateSessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public InProgressSessionCleaner(DebateSessionRepository sessionRepository,
                                    PlatformTransactionManager transactionManager, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        int aborted = abortInProgressSessions();
        if (aborted > 0) {
            log.warn("[재시작 정리] 진행 중이던 게임 {}건을 ABORTED 로 정리했습니다.", aborted);
        }
    }

    /** 진행 중인 세션을 모두 ABORTED 로 끝내고, 실제로 끝낸 수를 돌려줍니다. */
    public int abortInProgressSessions() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> sessionIds = sessionRepository.findAllByStatus(SessionStatus.IN_PROGRESS).stream()
                .map(DebateSession::getId)
                .toList();
        int aborted = 0;
        for (Long sessionId : sessionIds) {
            Integer updated = transactionTemplate.execute(
                    status -> sessionRepository.finishIfInProgress(sessionId, FinishReason.ABORTED, now));
            if (updated != null && updated == 1) {
                aborted++;
            }
        }
        return aborted;
    }
}
