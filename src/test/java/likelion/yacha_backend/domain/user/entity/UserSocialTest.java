package likelion.yacha_backend.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("소셜 계정")
class UserSocialTest {

    @Test
    @DisplayName("소셜 계정은 비밀번호가 없고 provider · providerId 를 갖는다")
    void createSocial() {
        User user = User.createSocial(Provider.KAKAO, "kakao-sub-1", "a@example.com", "수민");

        assertThat(user.getProvider()).isEqualTo(Provider.KAKAO);
        assertThat(user.getProviderId()).isEqualTo("kakao-sub-1");
        assertThat(user.getPassword()).isNull();
        assertThat(user.isGuest()).isFalse();
    }

    @Test
    @DisplayName("카카오는 이메일 동의가 선택이라 이메일 없이도 만들어진다")
    void createSocialWithoutEmail() {
        User user = User.createSocial(Provider.KAKAO, "kakao-sub-2", null, "야차37");

        assertThat(user.getEmail()).isNull();
    }

    @Test
    @DisplayName("이메일 가입 · 게스트는 provider 가 LOCAL 이다")
    void localAccounts() {
        assertThat(User.createMember("a@example.com", "hash", "수민").getProvider())
                .isEqualTo(Provider.LOCAL);
        assertThat(User.createGuest("야차37").getProvider()).isEqualTo(Provider.LOCAL);
    }

    @Test
    @DisplayName("이메일 가입 계정에 소셜을 연결해도 비밀번호는 남는다 (두 방법 모두 로그인 가능)")
    void linkSocialKeepsPassword() {
        User user = User.createMember("a@example.com", "hash", "수민");

        user.linkSocial(Provider.GOOGLE, "google-sub-1");

        assertThat(user.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(user.getProviderId()).isEqualTo("google-sub-1");
        assertThat(user.getPassword()).isEqualTo("hash");
    }

    @Test
    @DisplayName("이미 다른 소셜에 연결된 계정은 다시 연결할 수 없다")
    void linkSocialTwice() {
        User user = User.createSocial(Provider.KAKAO, "kakao-sub-1", "a@example.com", "수민");

        assertThatThrownBy(() -> user.linkSocial(Provider.GOOGLE, "google-sub-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("소셜 계정의 provider 는 LOCAL 일 수 없다")
    void socialProviderCannotBeLocal() {
        assertThatThrownBy(() -> User.createSocial(Provider.LOCAL, "x", "a@example.com", "수민"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
