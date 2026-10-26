package likelion.yacha_backend.domain.philosophy.entity;

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
import likelion.yacha_backend.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1분 철학 북마크. 마이페이지에서 북마크한 순서(최신순)로 모아 봄
 *
 * 해제하면 행을 지움. 같은 주제를 두 번 북마크할 수 없음
 */
@Entity
@Table(
        name = "philosophy_bookmark",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_philosophy_bookmark_user_weekly", columnNames = {"user_id", "weekly_philosophy_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhilosophyBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weekly_philosophy_id", nullable = false)
    private WeeklyPhilosophy weeklyPhilosophy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PhilosophyBookmark create(User user, WeeklyPhilosophy weeklyPhilosophy, LocalDateTime now) {
        PhilosophyBookmark bookmark = new PhilosophyBookmark();
        bookmark.user = Objects.requireNonNull(user, "user");
        bookmark.weeklyPhilosophy = Objects.requireNonNull(weeklyPhilosophy, "weeklyPhilosophy");
        bookmark.createdAt = Objects.requireNonNull(now, "now");
        return bookmark;
    }
}
