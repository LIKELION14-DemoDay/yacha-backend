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

    public static User createGuest(String nickname) {
        User user = new User();
        user.nickname = nickname;
        user.isGuest = true;
        user.role = Role.USER;
        return user;
    }

    public static User createMember(String email, String encodedPassword, String nickname) {
        User user = new User();
        user.email = email;
        user.password = encodedPassword;
        user.nickname = nickname;
        user.isGuest = false;
        user.role = Role.USER;
        return user;
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
