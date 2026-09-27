package likelion.yacha_backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import likelion.yacha_backend.global.entity.BaseTimeEntity;
import likelion.yacha_backend.global.security.jwt.Role;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(
        name = "users",
        // 같은 소셜 계정으로 두 번 가입되지 않게 합니다.
        // LOCAL 은 provider_id 가 NULL 이고, UNIQUE 는 NULL 을 중복으로 보지 않아 여러 행이 가능합니다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider", columnNames = {"provider", "provider_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false)
    private boolean isGuest;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    /** 어디서 가입했는지. 이메일 가입과 게스트는 {@link Provider#LOCAL}. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /**
     * 소셜에서 내려주는 고유 id({@code sub}). LOCAL 은 NULL.
     *
     * <p>이메일이 아니라 이 값으로 사용자를 찾습니다. 이메일은 사용자가 바꿀 수 있어서
     * 식별자로 쓰면 다른 사람 계정에 연결될 수 있습니다.
     */
    @Column(name = "provider_id", length = 255)
    private String providerId;

    public static User createGuest(String nickname) {
        User user = new User();
        user.nickname = nickname;
        user.isGuest = true;
        user.role = Role.USER;
        user.provider = Provider.LOCAL;
        return user;
    }

    public static User createMember(String email, String encodedPassword, String nickname) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.nickname = nickname;
        user.isGuest = false;
        user.role = Role.USER;
        user.provider = Provider.LOCAL;
        return user;
    }

    /**
     * 카카오 · 구글로 처음 로그인한 사용자.
     *
     * <p><b>비밀번호가 없습니다.</b> 게스트와 같은 구조라 로그인 쪽 코드는 그대로 동작합니다
     * (비밀번호가 NULL 이면 이메일 로그인이 실패합니다).
     *
     * @param email 카카오는 이메일 제공이 <b>선택 동의</b>라 NULL 일 수 있습니다.
     *              그 계정은 소셜로만 로그인할 수 있고 비밀번호 재설정도 쓸 수 없습니다.
     */
    public static User createSocial(Provider provider, String providerId, String email, String nickname) {
        if (provider == Provider.LOCAL) {
            throw new IllegalArgumentException("소셜 계정의 provider 가 LOCAL 일 수 없습니다.");
        }
        User user = new User();
        user.provider = provider;
        user.providerId = providerId;
        user.email = email;
        user.nickname = nickname;
        user.isGuest = false;
        user.role = Role.USER;
        return user;
    }

    /**
     * 이메일로 가입했던 계정에 소셜 로그인을 연결합니다. (같은 이메일일 때 자동 연결 — 팀 결정 A안)
     *
     * <p>비밀번호는 지우지 않습니다. 연결한 뒤에도 이메일 로그인을 계속 쓸 수 있습니다.
     *
     * <p>호출하는 쪽에서 <b>소셜이 확인한 이메일인지</b>(구글 {@code email_verified}) 먼저 검사해야
     * 합니다. 검사 없이 연결하면 남의 이메일을 적은 소셜 계정으로 그 사람 계정에 들어갈 수 있습니다.
     */
    public void linkSocial(Provider provider, String providerId) {
        if (provider == Provider.LOCAL) {
            throw new IllegalArgumentException("소셜 계정의 provider 가 LOCAL 일 수 없습니다.");
        }
        if (this.provider != Provider.LOCAL) {
            throw new IllegalStateException(
                    "이미 다른 소셜 계정에 연결돼 있습니다. userId=" + id + ", provider=" + this.provider);
        }
        this.provider = provider;
        this.providerId = providerId;
    }

    public void upgradeToMember(String email, String encodedPassword, String nickname) {
        if (!isGuest) {
            throw new IllegalStateException("이미 회원인 사용자는 승격할 수 없습니다. userId=" + id);
        }
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.isGuest = false;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }
}
