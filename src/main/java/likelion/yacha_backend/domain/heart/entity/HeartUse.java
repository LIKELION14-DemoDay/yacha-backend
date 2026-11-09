package likelion.yacha_backend.domain.heart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 판에 쓴 하트 하나. 게임이 무효로 끝나거나 판정이 끝내 실패하면 이 기록으로 돌려줍니다.
 *
 * <p>한 사람은 한 판에 한 번만 씁니다 (세션 + 사용자 유일). 돌려줄 때는 {@code refunded} 를 조건부 UPDATE 로 바꿔,
 * 무효 처리와 판정 실패 처리가 겹쳐도 한 번만 돌려줍니다. 기록이 없는 판(하트 기능 전에 시작한 판)은 돌려주지 않습니다.
 */
@Entity
@Table(name = "heart_use", uniqueConstraints = @UniqueConstraint(
        name = "uk_heart_use_session_user", columnNames = {"session_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HeartUse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private DebateSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private boolean refunded;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    public static HeartUse of(DebateSession session, User user, LocalDateTime usedAt) {
        HeartUse use = new HeartUse();
        use.session = Objects.requireNonNull(session, "session");
        use.user = Objects.requireNonNull(user, "user");
        use.usedAt = Objects.requireNonNull(usedAt, "usedAt");
        return use;
    }
}
