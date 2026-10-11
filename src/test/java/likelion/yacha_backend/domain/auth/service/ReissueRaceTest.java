package likelion.yacha_backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.auth.repository.InMemoryRefreshTokenStore;
import likelion.yacha_backend.domain.auth.repository.RefreshTokenStore;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.security.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재발급이 저장된 토큰을 확인한 "직후"에 다른 요청이 끼어드는 경우
 *
 * 실제로 동시에 돌리면 순서를 맞출 수 없어서, 저장소 조회(find) 바로 뒤에
 * 끼어드는 요청을 실행하는 저장소로 바꿔 끼워 순서를 고정함
 */
@SpringBootTest
@Transactional
@Import(ReissueRaceTest.InterleavingStoreConfig.class)
@DisplayName("재발급 중 끼어드는 요청")
class ReissueRaceTest {

    @TestConfiguration
    static class InterleavingStoreConfig {

        @Bean
        @Primary
        InterleavingRefreshTokenStore interleavingRefreshTokenStore(JwtProperties jwtProperties) {
            return new InterleavingRefreshTokenStore(new InMemoryRefreshTokenStore(jwtProperties));
        }
    }

    /** 다음 find 한 번이 끝난 직후에 지정한 동작을 실행하는 저장소 */
    static class InterleavingRefreshTokenStore implements RefreshTokenStore {

        private final RefreshTokenStore delegate;
        private Runnable afterNextFind;

        InterleavingRefreshTokenStore(RefreshTokenStore delegate) {
            this.delegate = delegate;
        }

        void afterNextFind(Runnable action) {
            this.afterNextFind = action;
        }

        @Override
        public Optional<String> find(Long userId) {
            Optional<String> found = delegate.find(userId);
            Runnable action = afterNextFind;
            afterNextFind = null;   // 끼어든 요청 안의 find에서 다시 실행되지 않게 먼저 비움
            if (action != null) {
                action.run();
            }
            return found;
        }

        @Override
        public void save(Long userId, String token) {
            delegate.save(userId, token);
        }

        @Override
        public boolean replace(Long userId, String expected, String next) {
            return delegate.replace(userId, expected, next);
        }

        @Override
        public void delete(Long userId) {
            delegate.delete(userId);
        }
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private InterleavingRefreshTokenStore store;

    @Autowired
    private UserRepository userRepository;

    private User user;
    private String refreshToken;

    @BeforeEach
    void login() {
        user = userRepository.save(User.createMember("race@example.com", "encoded", "수민"));
        refreshToken = tokenIssuer.issue(user).refreshToken();
    }

    private void expectInvalidRefreshToken() {
        assertThatThrownBy(() -> authService.reissue(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("확인 직후 로그아웃이 끼어들면 재발급은 실패하고 세션이 되살아나지 않는다")
    void logoutInBetween() {
        store.afterNextFind(() -> tokenIssuer.revoke(user.getId()));

        expectInvalidRefreshToken();

        assertThat(store.find(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("확인 직후 비밀번호 변경이 끼어들면 재발급은 실패하고 재발급한 쪽 토큰으로 덮어쓰지 않는다")
    void passwordChangeInBetween() {
        // 비밀번호 변경은 TokenIssuer.issue로 사용자의 새 토큰을 저장함
        AtomicReference<IssuedTokens> changed = new AtomicReference<>();
        store.afterNextFind(() -> changed.set(tokenIssuer.issue(user)));

        expectInvalidRefreshToken();

        // 재사용으로 보고 폐기하므로 사용자도 다시 로그인해야 하지만, 재발급한 쪽 세션이 남지는 않음
        assertThat(changed.get()).isNotNull();
        assertThat(store.find(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("같은 토큰으로 거의 동시에 두 번 재발급하면 늦은 쪽은 실패하고 재사용으로 보고 폐기한다")
    void concurrentReissue() {
        AtomicReference<IssuedTokens> first = new AtomicReference<>();
        store.afterNextFind(() -> first.set(authService.reissue(refreshToken)));

        expectInvalidRefreshToken();

        // 먼저 성공한 쪽의 새 토큰까지 폐기됨 (지금까지는 둘 다 통과해서 재사용 탐지를 피했음)
        assertThat(first.get()).isNotNull();
        assertThat(store.find(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("끼어드는 요청이 없으면 그대로 재발급된다")
    void noInterleaving() {
        IssuedTokens reissued = authService.reissue(refreshToken);

        assertThat(store.find(user.getId())).contains(reissued.refreshToken());
    }
}
