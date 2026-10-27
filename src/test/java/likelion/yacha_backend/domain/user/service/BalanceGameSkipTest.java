package likelion.yacha_backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import likelion.yacha_backend.domain.user.entity.BalanceGameStatus;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 밸런스 게임 건너뛰기 — 읽어 둔 값이 아니라 DB 의 지금 상태로 판단하는지.
 *
 * <p>결과 제출이 건너뛰기 트랜잭션 도중에 커밋되는 경우를, 사용자를 먼저 읽어 둔 뒤 벌크 UPDATE 로 결과를 넣어 흉내 냅니다
 * (벌크 UPDATE 는 읽어 둔 엔티티를 고치지 않아, 엔티티는 낡은 {@code NOT_STARTED} 로 남음).
 */
@SpringBootTest
@Transactional
@DisplayName("밸런스 게임 건너뛰기 — 조건부 UPDATE")
class BalanceGameSkipTest {

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
    @DisplayName("건너뛰는 사이 결과 제출이 먼저 들어갔으면 결과 · 유형을 덮어쓰지 않는다")
    void doesNotOverwriteResultCommittedInBetween() {
        Long userId = newUser();
        entityManager.flush();
        entityManager.clear();
        // 건너뛰기가 사용자를 읽어 둔 시점 (NOT_STARTED)
        userRepository.findById(userId).orElseThrow();
        // 그 사이 다른 요청의 결과 제출이 커밋됨
        entityManager.createQuery("update User u set u.philosopherType = :type, u.balanceGameStatus = :done where u.id = :id")
                .setParameter("type", PhilosopherType.KANT)
                .setParameter("done", BalanceGameStatus.DONE)
                .setParameter("id", userId)
                .executeUpdate();

        userService.skipBalanceGame(userId);

        User user = reload(userId);
        assertThat(user.getBalanceGameStatus()).isEqualTo(BalanceGameStatus.DONE);
        assertThat(user.getPhilosopherType()).isEqualTo(PhilosopherType.KANT);
    }

    @Test
    @DisplayName("결과가 없으면 SKIPPED 로 바뀐다")
    void skipsWithoutResult() {
        Long userId = newUser();

        userService.skipBalanceGame(userId);

        User user = reload(userId);
        assertThat(user.getBalanceGameStatus()).isEqualTo(BalanceGameStatus.SKIPPED);
        assertThat(user.getPhilosopherType()).isNull();
    }

    @Test
    @DisplayName("없는 사용자면 USER_NOT_FOUND")
    void unknownUser() {
        assertThatThrownBy(() -> userService.skipBalanceGame(Long.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().name())
                .isEqualTo("USER_NOT_FOUND");
    }
}
