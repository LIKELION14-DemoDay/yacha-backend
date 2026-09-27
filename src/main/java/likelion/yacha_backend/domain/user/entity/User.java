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
        // 같은 소셜 계정으로 두 번 가입되지 않게 함
        // LOCAL은 provider_id가 NULL이고, UNIQUE는 NULL을 중복으로 보지 않아 여러 행이 가능
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
     * 이메일이 아니라 이 값으로 사용자를 찾음
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
     * 비밀번호가 없음. 게스트와 같은 구조라 로그인 쪽 코드는 그대로 동작
     * (비밀번호가 NULL이면 이메일 로그인이 실패)
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
     * 이메일로 가입했던 계정에 소셜 로그인을 연결 (같은 이메일일 때 자동 연결)
     * 비밀번호는 지우지 않음. 연결한 뒤에도 이메일 로그인을 계속 사용 가능
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
