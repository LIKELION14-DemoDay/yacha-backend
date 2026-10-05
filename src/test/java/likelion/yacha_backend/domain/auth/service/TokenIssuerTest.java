package likelion.yacha_backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.repository.RefreshTokenStore;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.security.jwt.AuthUser;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("토큰 발급")
class TokenIssuerTest {

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    private User savedGuest() {
        return userRepository.save(User.createGuest("야차1234"));
    }

    @Test
    @DisplayName("게스트의 액세스 토큰에는 userId 와 GUEST 가 담긴다")
    void guestTokenCarriesGuestRole() {
        User user = savedGuest();

        IssuedTokens tokens = tokenIssuer.issue(user);

        AuthUser authUser = jwtTokenProvider.parseAccessUser(tokens.accessToken()).orElseThrow();
        assertThat(authUser.getUserId()).isEqualTo(user.getId());
        assertThat(authUser.role()).isEqualTo(Role.GUEST);
    }

    @Test
    @DisplayName("회원의 액세스 토큰에는 USER 가 담긴다")
    void memberTokenCarriesUserRole() {
        User user = userRepository.save(User.createMember("member@example.com", "encoded", "회원"));

        IssuedTokens tokens = tokenIssuer.issue(user);

        assertThat(jwtTokenProvider.parseAccessUser(tokens.accessToken()).orElseThrow().role())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("승격하면 다음 발급부터 USER 가 담긴다")
    void upgradedGuestGetsUserRole() {
        User user = savedGuest();
        user.upgradeToMember("upgraded@example.com", "encoded", "승격");

        IssuedTokens tokens = tokenIssuer.issue(user);

        assertThat(jwtTokenProvider.parseAccessUser(tokens.accessToken()).orElseThrow().role())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("리프레시 토큰이 저장소에 기록된다")
    void refreshTokenIsStored() {
        User user = savedGuest();

        IssuedTokens tokens = tokenIssuer.issue(user);

        assertThat(refreshTokenStore.find(user.getId())).contains(tokens.refreshToken());
    }

    @Test
    @DisplayName("리프레시 토큰은 액세스 토큰으로 쓸 수 없다")
    void refreshTokenCannotAuthenticate() {
        User user = savedGuest();

        IssuedTokens tokens = tokenIssuer.issue(user);

        assertThat(jwtTokenProvider.parseAccessUser(tokens.refreshToken())).isEmpty();
    }

    @Test
    @DisplayName("다시 발급하면 저장소의 이전 리프레시 토큰이 교체된다 (rotation)")
    void reissueReplacesStoredToken() {
        User user = savedGuest();

        refreshTokenStore.save(user.getId(), "old-refresh-token");

        IssuedTokens issued = tokenIssuer.issue(user);

        assertThat(refreshTokenStore.find(user.getId()).orElseThrow())
                .isEqualTo(issued.refreshToken())
                .isNotEqualTo("old-refresh-token");
    }

    @Test
    @DisplayName("revoke 하면 저장된 토큰이 사라진다 (로그아웃)")
    void revokeRemovesStoredToken() {
        User user = savedGuest();
        tokenIssuer.issue(user);

        tokenIssuer.revoke(user.getId());

        assertThat(refreshTokenStore.find(user.getId())).isEmpty();
    }
}
