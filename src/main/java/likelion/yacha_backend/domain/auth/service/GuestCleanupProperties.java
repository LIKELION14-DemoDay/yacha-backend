package likelion.yacha_backend.domain.auth.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml의 {@code auth.guest-cleanup.*}
 *
 * 값이 빠지면 첫 실행(새벽 4시)이 돼서야 실패하므로 기동 단계에서 검증함
 * (batchSize 가 빠지면 0, inactiveAfter 가 빠지면 null 로 바인딩됨)
 *
 * @param inactiveAfter 만든 지 이만큼 지난 게스트만 정리 대상으로 봄. 리프레시 토큰 수명(14일)과 맞춤
 * @param batchSize     한 트랜잭션에서 지우는 최대 수. 크면 잠금이 길어지고, 작으면 왕복이 늘어남
 * @param cron          정리 작업을 도는 시각. 사용자가 적은 새벽에 돔
 */
@Validated
@ConfigurationProperties(prefix = "auth.guest-cleanup")
public record GuestCleanupProperties(
        @NotNull Duration inactiveAfter,
        @Positive int batchSize,
        @NotBlank String cron
) {
}
