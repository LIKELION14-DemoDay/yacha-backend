package likelion.yacha_backend.domain.heart.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserHeart — 하트 계산 (최대 3개 · 15분 충전)")
class UserHeartTest {

    private static final int MAX = 3;
    private static final Duration INTERVAL = Duration.ofMinutes(15);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 6, 10, 0);

    private static UserHeart full() {
        return UserHeart.full(User.createGuest("게스트"), MAX, T0);
    }

    private static LocalDateTime at(int minutes) {
        return T0.plusMinutes(minutes);
    }

    @Test
    @DisplayName("처음에는 가득 차 있고, 다음 충전 시각이 없다")
    void startsFull() {
        UserHeart heart = full();

        assertThat(heart.current(at(0), MAX, INTERVAL)).isEqualTo(3);
        assertThat(heart.nextChargeAt(at(0), MAX, INTERVAL)).isNull();
    }

    @Test
    @DisplayName("가득 찬 상태에서 하나 쓰면 그때부터 15분 타이머가 돈다")
    void timerStartsOnFirstUse() {
        UserHeart heart = full();
        heart.consume(at(7), MAX, INTERVAL);

        assertThat(heart.current(at(7), MAX, INTERVAL)).isEqualTo(2);
        assertThat(heart.nextChargeAt(at(7), MAX, INTERVAL)).isEqualTo(at(22));
        assertThat(heart.current(at(21), MAX, INTERVAL)).isEqualTo(2);
        assertThat(heart.current(at(22), MAX, INTERVAL)).isEqualTo(3);
        assertThat(heart.nextChargeAt(at(22), MAX, INTERVAL)).isNull();
    }

    @Test
    @DisplayName("10:00 · 10:05 · 10:10 에 세 판 하면 10:15 · 10:30 · 10:45 에 하나씩 찬다 (쌓이던 시간을 잃지 않음)")
    void keepsRunningTimer() {
        UserHeart heart = full();
        heart.consume(at(0), MAX, INTERVAL);
        heart.consume(at(5), MAX, INTERVAL);
        heart.consume(at(10), MAX, INTERVAL);

        assertThat(heart.current(at(10), MAX, INTERVAL)).isZero();
        assertThat(heart.nextChargeAt(at(10), MAX, INTERVAL)).isEqualTo(at(15));
        assertThat(heart.current(at(15), MAX, INTERVAL)).isEqualTo(1);
        assertThat(heart.nextChargeAt(at(15), MAX, INTERVAL)).isEqualTo(at(30));
        assertThat(heart.current(at(30), MAX, INTERVAL)).isEqualTo(2);
        assertThat(heart.current(at(45), MAX, INTERVAL)).isEqualTo(3);
        assertThat(heart.current(at(600), MAX, INTERVAL)).isEqualTo(3);
    }

    @Test
    @DisplayName("덜 찬 상태에서 쓰면 충전된 만큼만 반영하고 타이머는 이어서 돈다")
    void consumeAfterPartialRecharge() {
        UserHeart heart = full();
        heart.consume(at(0), MAX, INTERVAL);
        heart.consume(at(0), MAX, INTERVAL);
        heart.consume(at(0), MAX, INTERVAL);

        // 20분 뒤: 1개 찼고 다음은 30분. 하나 쓰면 0개, 다음 충전은 그대로 30분
        assertThat(heart.consume(at(20), MAX, INTERVAL)).isTrue();
        assertThat(heart.current(at(20), MAX, INTERVAL)).isZero();
        assertThat(heart.nextChargeAt(at(20), MAX, INTERVAL)).isEqualTo(at(30));
    }

    @Test
    @DisplayName("0개면 쓸 수 없고 아무것도 바뀌지 않는다")
    void cannotConsumeWhenEmpty() {
        UserHeart heart = full();
        for (int i = 0; i < MAX; i++) {
            heart.consume(at(0), MAX, INTERVAL);
        }

        assertThat(heart.consume(at(1), MAX, INTERVAL)).isFalse();
        assertThat(heart.current(at(1), MAX, INTERVAL)).isZero();
        assertThat(heart.nextChargeAt(at(1), MAX, INTERVAL)).isEqualTo(at(15));
    }

    @Test
    @DisplayName("돌려받으면 1개 늘고, 돌던 타이머는 그대로다")
    void refundKeepsTimer() {
        UserHeart heart = full();
        heart.consume(at(0), MAX, INTERVAL);
        heart.consume(at(5), MAX, INTERVAL);

        heart.refund(at(6), MAX, INTERVAL);

        assertThat(heart.current(at(6), MAX, INTERVAL)).isEqualTo(2);
        assertThat(heart.nextChargeAt(at(6), MAX, INTERVAL)).isEqualTo(at(15));
    }

    @Test
    @DisplayName("돌려받아도 최대를 넘지 않는다")
    void refundCapsAtMax() {
        UserHeart heart = full();
        heart.consume(at(0), MAX, INTERVAL);

        // 15분 뒤에는 이미 가득 참
        heart.refund(at(15), MAX, INTERVAL);
        heart.refund(at(16), MAX, INTERVAL);

        assertThat(heart.current(at(16), MAX, INTERVAL)).isEqualTo(3);
        assertThat(heart.nextChargeAt(at(16), MAX, INTERVAL)).isNull();
    }

    @Test
    @DisplayName("최대 개수 설정을 줄이면 저장된 값과 상관없이 새 최대를 넘지 않는다")
    void respectsLoweredMax() {
        UserHeart heart = full();

        assertThat(heart.current(at(0), 2, INTERVAL)).isEqualTo(2);
        assertThat(heart.consume(at(0), 2, INTERVAL)).isTrue();
        assertThat(heart.current(at(0), 2, INTERVAL)).isEqualTo(1);
    }

    @Test
    @DisplayName("시계가 기준 시각보다 앞으로 돌아가도 하트가 줄지 않는다")
    void ignoresClockGoingBack() {
        UserHeart heart = full();
        heart.consume(at(10), MAX, INTERVAL);

        assertThat(heart.current(at(5), MAX, INTERVAL)).isEqualTo(2);
        assertThat(heart.nextChargeAt(at(5), MAX, INTERVAL)).isEqualTo(at(25));
    }
}
