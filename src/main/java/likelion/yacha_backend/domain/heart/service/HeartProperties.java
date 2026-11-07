package likelion.yacha_backend.domain.heart.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code heart.*} — 무료 하트(생명).
 *
 * <p>값이 빠지거나 범위를 벗어나면 하트 계산이 깨지므로 기동 단계에서 실패시킵니다.
 *
 * @param max              최대 하트 수 (3). 처음에 이만큼 갖고 시작합니다
 * @param rechargeInterval 하트 1개가 다시 차는 간격 (15분). 하나 쓰면 그때부터 셉니다
 */
@Validated
@ConfigurationProperties(prefix = "heart")
public record HeartProperties(
        @Positive int max,
        @NotNull Duration rechargeInterval
) {

    /** 0 이하면 충전 계산에서 0 으로 나누거나 거꾸로 셉니다. */
    @AssertTrue(message = "heart.recharge-interval 은 0 보다 커야 합니다.")
    public boolean isRechargeIntervalPositive() {
        return rechargeInterval == null || (!rechargeInterval.isZero() && !rechargeInterval.isNegative());
    }
}
