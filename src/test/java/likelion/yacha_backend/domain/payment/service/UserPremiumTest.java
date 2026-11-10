package likelion.yacha_backend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("프리미엄 패스 · 공격권")
class UserPremiumTest {

    private static User member() {
        return User.createMember("pay@example.com", "encoded", "결제자");
    }

    @Test
    @DisplayName("새 회원은 프리미엄이 아니고 공격권 0")
    void defaults() {
        User user = member();

        assertThat(user.isPremium()).isFalse();
        assertThat(user.getAttackTickets()).isZero();
    }

    @Test
    @DisplayName("프리미엄 패스를 받으면 프리미엄, 다시 받아도 그대로")
    void grantPremium() {
        User user = member();

        user.grantPremium();
        user.grantPremium();

        assertThat(user.isPremium()).isTrue();
    }

    @Test
    @DisplayName("공격권은 결제마다 1개씩 쌓임")
    void addAttackTicket() {
        User user = member();

        user.addAttackTicket();
        user.addAttackTicket();

        assertThat(user.getAttackTickets()).isEqualTo(2);
    }
}
