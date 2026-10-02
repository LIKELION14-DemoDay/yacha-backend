package likelion.yacha_backend.domain.auth.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 {@code auth.guest-cleanup.*}
 *
 * @param inactiveAfter 만든 지 이만큼 지난 게스트만 정리 대상으로 봄. 리프레시 토큰 수명(14일)과 맞춤
 * @param batchSize     한 트랜잭션에서 지우는 최대 수. 크면 잠금이 길어지고, 작으면 왕복이 늘어남
 * @param cron          정리 작업을 도는 시각. 사용자가 적은 새벽에 돔
 */
@ConfigurationProperties(prefix = "auth.guest-cleanup")
public record GuestCleanupProperties(Duration inactiveAfter, int batchSize, String cron) {
}
