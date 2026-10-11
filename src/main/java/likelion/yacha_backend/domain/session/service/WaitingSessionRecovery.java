package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.session.repository.WaitingRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 서버가 뜰 때 대기 중인 랜덤 방의 대기 타이머를 다시 등록합니다 (명세 2-3-4 · 2-11).
 *
 * <p>타이머는 메모리에 있어 재시작하면 사라집니다. 다시 등록하지 않으면 재시작 전에 만든 대기 방은
 * 팝업도 5분 취소도 없이 대기열에 영원히 남습니다. 상한이 이미 지난 방은 등록하자마자 취소됩니다.
 *
 * <p>{@link ApplicationReadyEvent} 는 요청을 받기 시작한 뒤에 옵니다. 그 사이 만든 방은 생성할 때 이미 등록했으므로
 * <b>이 서버가 뜨기 전에 만든 방만</b> 등록합니다 ({@link InProgressSessionCleaner} 와 같은 기준).
 *
 * <p>실패해도 기동은 막지 않고 로그만 남깁니다. 서버 1대 전제입니다.
 *
 * <p>같은 방식으로 주기 정리({@link #sweepExpired})도 합니다. 5분 취소는 한 번만 도는 타이머라, 그때 DB 가 잠깐
 * 끊기거나 생성 직후 등록이 실패하면 방이 {@code WAITING} 으로 남아 방장이 {@code ALREADY_IN_SESSION} 으로 막힙니다.
 * 원인과 상관없이 상한이 지난 방을 다시 등록하면 바로 취소됩니다.
 */
@Slf4j
@Service
public class WaitingSessionRecovery {

    private final DebateSessionRepository sessionRepository;
    private final WaitTimerService waitTimerService;
    private final WaitTimerProperties properties;
    private final Clock clock;
    /** 이 서버가 뜬 시각. 이보다 먼저 만든 방만 이전 서버의 방입니다. */
    private final LocalDateTime bootTime;

    public WaitingSessionRecovery(DebateSessionRepository sessionRepository, WaitTimerService waitTimerService,
                                  WaitTimerProperties properties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.waitTimerService = waitTimerService;
        this.properties = properties;
        this.clock = clock;
        this.bootTime = LocalDateTime.now(clock);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            int restored = restore(bootTime);
            if (restored > 0) {
                log.info("[재시작 복구] 대기 방 {}건의 대기 타이머를 다시 등록했습니다.", restored);
            }
        } catch (RuntimeException e) {
            log.error("[재시작 복구] 대기 방을 조회하지 못했습니다.", e);
        }
    }

    /**
     * 주기 정리. 상한보다 정리 간격만큼 더 지났는데도 대기 중인 방을 다시 등록해 바로 취소합니다.
     * 정상 방은 그 전에 만료 타이머가 취소하므로 걸리지 않고, 걸려도 취소는 조건부 UPDATE 라 한 번만 됩니다.
     * 스케줄링은 SchedulingConfig 가 켭니다 (테스트에서는 꺼져 있어 직접 부릅니다).
     */
    @Scheduled(fixedDelayString = "${game.wait.sweep-interval}")
    public void sweepExpired() {
        LocalDateTime createdBefore = LocalDateTime.now(clock)
                .minus(properties.limit())
                .minus(properties.sweepInterval());
        try {
            int swept = restore(createdBefore);
            if (swept > 0) {
                log.warn("[대기 정리] 상한이 지났는데 대기 중인 방 {}건을 취소합니다.", swept);
            }
        } catch (RuntimeException e) {
            log.error("[대기 정리] 대기 방을 조회하지 못했습니다.", e);
        }
    }

    /** {@code createdBefore} 보다 먼저 만든 대기 방의 타이머를 등록하고, 등록한 방 수를 돌려줍니다. */
    public int restore(LocalDateTime createdBefore) {
        List<WaitingRoom> rooms = sessionRepository.findWaitingRandomRoomsCreatedBefore(createdBefore);
        int restored = 0;
        for (WaitingRoom room : rooms) {
            try {
                waitTimerService.register(room.sessionId(), room.hostUserId(), room.createdAt());
                restored++;
            } catch (RuntimeException e) {
                log.error("[대기 타이머] 등록하지 못했습니다. sessionId={}", room.sessionId(), e);
            }
        }
        return restored;
    }
}
