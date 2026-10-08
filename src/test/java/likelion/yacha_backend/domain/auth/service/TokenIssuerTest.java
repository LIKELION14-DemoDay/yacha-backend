package likelion.yacha_backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
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
    @DisplayName("rotate는 저장된 토큰이 확인한 값 그대로면 새 토큰으로 바꾼다")
    void rotateReplacesWhenUnchanged() {
        User user = savedGuest();
        String current = tokenIssuer.issue(user).refreshToken();

        Optional<IssuedTokens> rotated = tokenIssuer.rotate(user, current);

        assertThat(rotated).isPresent();
        assertThat(refreshTokenStore.find(user.getId())).contains(rotated.get().refreshToken());
    }

    @Test
    @DisplayName("rotate는 그 사이 저장소가 바뀌었으면 덮어쓰지 않고 비어 있다")
    void rotateKeepsNewerToken() {
        User user = savedGuest();
        String current = tokenIssuer.issue(user).refreshToken();
        IssuedTokens newer = tokenIssuer.issue(user);   // 비밀번호 변경 등으로 새로 발급됨

        assertThat(tokenIssuer.rotate(user, current)).isEmpty();
        assertThat(refreshTokenStore.find(user.getId())).contains(newer.refreshToken());
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
