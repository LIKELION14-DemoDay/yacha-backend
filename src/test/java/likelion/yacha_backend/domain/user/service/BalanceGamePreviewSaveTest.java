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
 * 로그인할 때 미리보기 결과 저장 — 아직 안 한 계정만, DB 의 지금 상태로 판단하는지
 */
@SpringBootTest
@Transactional
@DisplayName("밸런스 게임 미리보기 결과 저장 — 조건부 UPDATE")
class BalanceGamePreviewSaveTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private Long newUser() {
        return userRepository.save(User.createGuest("u" + UUID.randomUUID().toString().substring(0, 8))).getId();
    }

    private User reload(Long userId) {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(userId).orElseThrow();
    }

    @Test
    @DisplayName("아직 안 했으면 유형을 저장하고 DONE 이 된다")
    void savesType() {
        Long userId = newUser();

        assertThat(userService.saveBalanceGameIfNotStarted(userId, PhilosopherType.HOBBES)).isTrue();

        User user = reload(userId);
        assertThat(user.getBalanceGameStatus()).isEqualTo(BalanceGameStatus.DONE);
        assertThat(user.getPhilosopherType()).isEqualTo(PhilosopherType.HOBBES);
    }

    @Test
    @DisplayName("건너뜀이면 유형 없이 SKIPPED 가 된다")
    void savesSkipped() {
        Long userId = newUser();

        assertThat(userService.saveBalanceGameIfNotStarted(userId, null)).isTrue();

        User user = reload(userId);
        assertThat(user.getBalanceGameStatus()).isEqualTo(BalanceGameStatus.SKIPPED);
        assertThat(user.getPhilosopherType()).isNull();
    }

    @Test
    @DisplayName("이미 결과가 있으면 덮어쓰지 않는다")
    void keepsExistingResult() {
        Long userId = newUser();
        userService.completeBalanceGame(userId, PhilosopherType.KANT);

        assertThat(userService.saveBalanceGameIfNotStarted(userId, PhilosopherType.HOBBES)).isFalse();
        assertThat(userService.saveBalanceGameIfNotStarted(userId, null)).isFalse();

        User user = reload(userId);
        assertThat(user.getBalanceGameStatus()).isEqualTo(BalanceGameStatus.DONE);
        assertThat(user.getPhilosopherType()).isEqualTo(PhilosopherType.KANT);
    }

    @Test
    @DisplayName("이미 건너뛰었으면 결과로 바꾸지 않는다")
    void keepsSkipped() {
        Long userId = newUser();
        userService.skipBalanceGame(userId);

        assertThat(userService.saveBalanceGameIfNotStarted(userId, PhilosopherType.HOBBES)).isFalse();

        User user = reload(userId);
        assertThat(user.getBalanceGameStatus()).isEqualTo(BalanceGameStatus.SKIPPED);
        assertThat(user.getPhilosopherType()).isNull();
    }

    @Test
    @DisplayName("읽어 둔 값이 아니라 DB 의 지금 상태로 판단한다 (그 사이 저장된 결과를 덮어쓰지 않음)")
    void doesNotOverwriteResultCommittedInBetween() {
        Long userId = newUser();
        entityManager.flush();
        entityManager.clear();
        // 저장 전에 사용자를 읽어 둔 시점 (NOT_STARTED)
        userRepository.findById(userId).orElseThrow();
        // 그 사이 다른 요청의 결과 제출이 커밋됨
        entityManager.createQuery("update User u set u.philosopherType = :type, u.balanceGameStatus = :done where u.id = :id")
                .setParameter("type", PhilosopherType.KANT)
                .setParameter("done", BalanceGameStatus.DONE)
                .setParameter("id", userId)
                .executeUpdate();

        assertThat(userService.saveBalanceGameIfNotStarted(userId, PhilosopherType.HOBBES)).isFalse();

        assertThat(reload(userId).getPhilosopherType()).isEqualTo(PhilosopherType.KANT);
    }

    @Test
    @DisplayName("없는 사용자면 저장하지 않고 false (로그인 응답을 막지 않음)")
    void unknownUser() {
        assertThat(userService.saveBalanceGameIfNotStarted(Long.MAX_VALUE, PhilosopherType.HOBBES)).isFalse();
    }
}
