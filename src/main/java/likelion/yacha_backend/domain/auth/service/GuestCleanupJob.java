package likelion.yacha_backend.domain.auth.service;

import java.time.Clock;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.auth.service.GuestCleanupService.Batch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래된 게스트 계정을 정해진 시각에 정리 ({@code auth.guest-cleanup.cron})
 *
 * 묶음마다 트랜잭션을 나눠, 지울 계정이 많아도 한 트랜잭션이 테이블을 오래 잡지 않게 함
 * 스케줄링은 SchedulingConfig가 켬 (테스트에서는 꺼져 있어 직접 {@link #run()}을 부름)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupJob {

    private final GuestCleanupService guestCleanupService;
    private final GuestCleanupProperties properties;
    private final Clock clock;

    /** 이번에 지운 계정 수 */
    @Scheduled(cron = "${auth.guest-cleanup.cron}")
    public int run() {
        LocalDateTime createdBefore = LocalDateTime.now(clock).minus(properties.inactiveAfter());

        int deleted = 0;
        long afterId = 0;
        Batch batch;
        do {
            batch = guestCleanupService.cleanUpBatch(createdBefore, afterId);
            deleted += batch.deleted();
            afterId = batch.lastScannedId();
        } while (batch.scanned() == properties.batchSize());

        log.info("오래된 게스트 계정 정리: deleted={}", deleted);
        return deleted;
    }
}
