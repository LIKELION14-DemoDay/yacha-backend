package likelion.yacha_backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import likelion.yacha_backend.global.entity.BaseTimeEntity;
import likelion.yacha_backend.global.security.jwt.Role;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name = "users")
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

    /** 게스트 생성. email · password 는 NULL 로 둠. */
    public static User createGuest(String nickname) {
        User user = new User();
        user.nickname = nickname;
        user.isGuest = true;
        user.role = Role.USER;
        return user;
    }

    /** 회원 생성 */
    public static User createMember(String email, String encodedPassword, String nickname) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.nickname = nickname;
        user.isGuest = false;
        user.role = Role.USER;
        return user;
    }

    /** 게스트를 회원으로 승격. 같은 행을 수정하므로 토론 기록이 그대로 유지. */
    public void upgradeToMember(String email, String encodedPassword, String nickname) {
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.isGuest = false;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }
}
