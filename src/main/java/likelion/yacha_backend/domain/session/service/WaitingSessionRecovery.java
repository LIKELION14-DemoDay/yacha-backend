
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
 * 서버 시작 시 대기 중인 랜덤·친구 방의 타이머를 복구하고, 놓친 만료 처리를 주기적으로 정리합니다.
 *
 * <p>타이머는 메모리에 있기 때문에 재시작 시 사라집니다. 이를 복구하지 않으면
 * 이전 서버에서 생성한 대기 방이 계속 WAITING으로 남고 방장이 ALREADY_IN_SESSION으로 막힙니다.
 *
 * <p>랜덤 방: 30초마다 WAIT_PROMPT, 5분 뒤 WAIT_EXPIRED.
 * 친구 방: 공통 FRIEND_LIMIT 뒤 INVITE_EXPIRED 및 CANCELLED.
 *
 * <p>ApplicationReadyEvent는 요청 수신이 시작된 뒤 발생합니다.
 * 중복 등록하지 않도록 서버 부팅 전에 생성된 방만 복구하고, 이미 만료된 방은 즉시 취소합니다.
 *
 * <p>타이머 실행 시 DB가 잠시 중단됐거나 등록 자체가 실패한 경우를 대비해
 * 일정 간격으로 지난 WAITING 방을 다시 조회합니다. 취소는 상태 조건부 UPDATE라
 * 동시 입장과 겹쳐도 한쪽만 성공하고 알림도 중복되지 않습니다.
 *
 * <p>방마다 실패를 별도로 처리하며 서버 1대 전제로 동작합니다.
 */
@Slf4j
@Service
public class WaitingSessionRecovery {

    private final DebateSessionRepository sessionRepository;
    private final WaitTimerService waitTimerService;
    private final WaitTimerProperties properties;
    private final Clock clock;

    /** 서버가 시작된 시각 */
    private final LocalDateTime bootTime;

    public WaitingSessionRecovery(
            DebateSessionRepository sessionRepository,
            WaitTimerService waitTimerService,
            WaitTimerProperties properties,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.waitTimerService = waitTimerService;
        this.properties = properties;
        this.clock = clock;
        this.bootTime = LocalDateTime.now(clock);
    }

    /**
     * 서버가 재시작되면 기존 대기 방의 타이머를 복구한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {

        // 기존 랜덤 방 복구
        try {
            int restored = restore(bootTime);

            if (restored > 0) {
                log.info(
                        "[재시작 복구] 랜덤 방 {}건 타이머 복구",
                        restored
                );
            }
        } catch (RuntimeException e) {
            log.error("[재시작 복구] 랜덤 방 복구 실패", e);
        }

        // 친구 초대 방 복구
        try {
            int restored = restoreFriends(bootTime);

            if (restored > 0) {
                log.info(
                        "[재시작 복구] 친구 방 {}건 타이머 복구",
                        restored
                );
            }
        } catch (RuntimeException e) {
            log.error("[재시작 복구] 친구 방 복구 실패", e);
        }
    }

    /**
     * 주기적으로 만료된 대기 방을 정리한다.
     */
    @Scheduled(fixedDelayString = "${game.wait.sweep-interval}")
    public void sweepExpired() {

        LocalDateTime now = LocalDateTime.now(clock);

        // 기존 랜덤 방 5분 만료 정리
        LocalDateTime randomCreatedBefore = now
                .minus(properties.limit())
                .minus(properties.sweepInterval());

        try {
            int swept = expireAll(randomCreatedBefore);

            if (swept > 0) {
                log.warn(
                        "[대기 정리] 랜덤 방 {}건 만료 처리",
                        swept
                );
            }
        } catch (RuntimeException e) {
            log.error("[대기 정리] 랜덤 방 정리 실패", e);
        }

        // 친구 초대 방 10분 만료 정리
        LocalDateTime friendCreatedBefore = now
                .minus(WaitTimerProperties.FRIEND_LIMIT)
                .minus(properties.sweepInterval());

        try {
            int swept = expireFriends(friendCreatedBefore);

            if (swept > 0) {
                log.warn(
                        "[대기 정리] 친구 방 {}건 만료 처리",
                        swept
                );
            }
        } catch (RuntimeException e) {
            log.error("[대기 정리] 친구 방 정리 실패", e);
        }
    }

    /**
     * 기존 랜덤 방 타이머 복구
     */
    public int restore(LocalDateTime createdBefore) {

        List<WaitingRoom> rooms =
                sessionRepository.findWaitingRandomRoomsCreatedBefore(
                        createdBefore
                );

        int restored = 0;

        for (WaitingRoom room : rooms) {
            try {
                waitTimerService.register(
                        room.sessionId(),
                        room.hostUserId(),
                        room.createdAt()
                );
                restored++;

            } catch (RuntimeException e) {
                log.error(
                        "[대기 타이머] 랜덤 방 복구 실패. sessionId={}",
                        room.sessionId(),
                        e
                );
            }
        }

        return restored;
    }

    /**
     * 기존 랜덤 방 만료 정리
     */
    int expireAll(LocalDateTime createdBefore) {

        List<WaitingRoom> rooms =
                sessionRepository.findWaitingRandomRoomsCreatedBefore(
                        createdBefore
                );

        int expired = 0;

        for (WaitingRoom room : rooms) {
            try {
                if (waitTimerService.expire(
                        room.sessionId(),
                        room.hostUserId()
                )) {
                    expired++;
                }

            } catch (RuntimeException e) {
                log.error(
                        "[대기 정리] 랜덤 방 정리 실패. sessionId={}",
                        room.sessionId(),
                        e
                );
            }
        }

        return expired;
    }

    /**
     * 친구 방 타이머 복구
     */
    public int restoreFriends(LocalDateTime createdBefore) {

        List<WaitingRoom> rooms =
                sessionRepository.findWaitingFriendRoomsCreatedBefore(
                        createdBefore
                );

        int restored = 0;

        for (WaitingRoom room : rooms) {
            try {
                waitTimerService.registerFriend(
                        room.sessionId(),
                        room.hostUserId(),
                        room.createdAt()
                );
                restored++;

            } catch (RuntimeException e) {
                log.error(
                        "[친구 초대] 타이머 복구 실패. sessionId={}",
                        room.sessionId(),
                        e
                );
            }
        }

        return restored;
    }

    /**
     * 10분이 지난 친구 방을 CANCELLED 처리
     */
    int expireFriends(LocalDateTime createdBefore) {

        List<WaitingRoom> rooms =
                sessionRepository.findWaitingFriendRoomsCreatedBefore(
                        createdBefore
                );

        int expired = 0;

        for (WaitingRoom room : rooms) {
            try {
                if (waitTimerService.expireFriend(
                        room.sessionId(),
                        room.hostUserId()
                )) {
                    expired++;
                }

            } catch (RuntimeException e) {
                log.error(
                        "[친구 초대] 만료 정리 실패. sessionId={}",
                        room.sessionId(),
                        e
                );
            }
        }

        return expired;
    }
}
