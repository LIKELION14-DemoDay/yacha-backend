package likelion.yacha_backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import likelion.yacha_backend.domain.user.entity.BalanceGameStatus;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 밸런스 게임 결과 제출 — 바꾼 칼럼만 UPDATE 하는지 ({@code User} 의 {@code @DynamicUpdate}).
 *
 * <p>회원 승격이 결과 제출 트랜잭션 도중에 커밋되는 경우를, 사용자를 먼저 읽어 둔 뒤 벌크 UPDATE 로 승격을 넣어 흉내 냅니다
 * (벌크 UPDATE 는 읽어 둔 엔티티를 고치지 않아, 엔티티는 낡은 게스트 상태로 남음).
 */
@SpringBootTest
@Transactional
@DisplayName("밸런스 게임 결과 제출 — 바꾼 칼럼만 저장")
class BalanceGameSubmitTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("결과를 내는 사이 회원 승격이 먼저 들어갔으면 승격을 되돌리지 않는다")
    void doesNotRevertUpgradeCommittedInBetween() {
        Long userId = userRepository.save(User.createGuest("u" + UUID.randomUUID().toString().substring(0, 8))).getId();
        String email = UUID.randomUUID() + "@test.com";
        entityManager.flush();
        entityManager.clear();
        // 결과 제출이 사용자를 읽어 둔 시점 (아직 게스트)
        userRepository.findById(userId).orElseThrow();
        // 그 사이 다른 요청의 회원 승격이 커밋됨
        entityManager.createQuery("""
                        update User u set u.email = :email, u.password = 'encoded', u.nickname = '회원', u.isGuest = false
                        where u.id = :id""")
                .setParameter("email", email)
                .setParameter("id", userId)
                .executeUpdate();

        userService.completeBalanceGame(userId, PhilosopherType.KANT);

        entityManager.flush();
        entityManager.clear();
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.isGuest()).isFalse();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(user.getNickname()).isEqualTo("회원");
        assertThat(user.getPhilosopherType()).isEqualTo(PhilosopherType.KANT);
        assertThat(user.getBalanceGameStatus()).isEqualTo(BalanceGameStatus.DONE);
    }
}
