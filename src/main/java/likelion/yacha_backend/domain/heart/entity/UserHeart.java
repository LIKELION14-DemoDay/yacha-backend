package likelion.yacha_backend.domain.heart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import likelion.yacha_backend.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 한 명의 하트. 스케줄러 없이 조회할 때 충전을 계산합니다.
 *
 * <p>기준 시각({@code chargedAt})의 하트 수만 저장합니다. 지금 하트는
 * {@code min(최대, hearts + (지금 − chargedAt) / 충전 간격)} 입니다. 서버가 꺼져 있던 시간도 그대로 셉니다.
 *
 * <ul>
 *   <li>가득 찬 상태에서 하나 쓰면 그때부터 타이머가 돕니다 ({@code chargedAt} = 지금).</li>
 *   <li>덜 찬 상태에서 쓰거나 돌려받으면, 그사이 찬 만큼만 {@code chargedAt} 을 앞으로 옮겨 쌓이던 시간을 잃지 않습니다.</li>
 * </ul>
 *
 * <p>행이 없는 사용자는 하트가 가득 찬 것으로 봅니다. 처음 쓸 때 행을 만듭니다.
 * 최대 개수 · 충전 간격은 설정값이라 메서드마다 받습니다. 최대 개수를 줄여도 저장된 값과 상관없이 최대를 넘지 않습니다.
 */
@Entity
@Table(name = "user_heart")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserHeart {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** {@code chargedAt} 시각의 하트 수 */
    @Column(nullable = false)
    private int hearts;

    /** 충전 기준 시각. 하트가 가득 차 있으면 의미가 없습니다 */
    @Column(name = "charged_at", nullable = false)
    private LocalDateTime chargedAt;

    /** 하트가 가득 찬 새 행. */
    public static UserHeart full(User user, int max, LocalDateTime now) {
        UserHeart heart = new UserHeart();
        heart.user = Objects.requireNonNull(user, "user");
        heart.hearts = max;
        heart.chargedAt = Objects.requireNonNull(now, "now");
        return heart;
    }

    /** 지금 하트 수. */
    public int current(LocalDateTime now, int max, Duration interval) {
        if (hearts >= max) {
            return max;
        }
        return (int) Math.min(max, hearts + charged(now, interval));
    }

    /** 다음 하트가 차는 시각. 가득 차 있으면 null 입니다. */
    public LocalDateTime nextChargeAt(LocalDateTime now, int max, Duration interval) {
        if (current(now, max, interval) >= max) {
            return null;
        }
        return chargedAt.plus(interval.multipliedBy(charged(now, interval) + 1));
    }

    /**
     * 하나 씁니다. 0개면 아무것도 바꾸지 않고 false 입니다.
     * 가득 찬 상태에서 쓰면 지금부터 타이머가 돕니다.
     */
    public boolean consume(LocalDateTime now, int max, Duration interval) {
        settle(now, max, interval);
        if (hearts <= 0) {
            return false;
        }
        if (hearts >= max) {
            hearts = max;
            chargedAt = now;
        }
        hearts--;
        return true;
    }

    /** 하나 돌려줍니다. 최대를 넘지 않고, 돌던 타이머는 그대로 둡니다. */
    public void refund(LocalDateTime now, int max, Duration interval) {
        settle(now, max, interval);
        if (hearts < max) {
            hearts++;
        }
    }

    /** 기준 시각 뒤에 찬 하트를 저장된 값에 반영하고, 반영한 만큼 기준 시각을 앞으로 옮깁니다. */
    private void settle(LocalDateTime now, int max, Duration interval) {
        if (hearts >= max) {
            hearts = max;
            return;
        }
        long charged = charged(now, interval);
        if (charged <= 0) {
            return;
        }
        if (hearts + charged >= max) {
            hearts = max;
            chargedAt = now;
            return;
        }
        hearts += (int) charged;
        chargedAt = chargedAt.plus(interval.multipliedBy(charged));
    }

    /** 기준 시각 뒤에 찬 개수. 시계가 거꾸로 가도 음수가 되지 않습니다. */
    private long charged(LocalDateTime now, Duration interval) {
        Duration elapsed = Duration.between(chargedAt, now);
        if (elapsed.isNegative()) {
            return 0;
        }
        return elapsed.dividedBy(interval);
    }
}
