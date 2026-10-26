package likelion.yacha_backend.domain.philosophy.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 1분 철학 글 한 편 (#98). 주제 하나와 그 주제에 의견이 있는 철학자 2~3명의 글
 *
 * 한 주에 여러 편(기본 5편)을 월요일에 한꺼번에 공개함
 * 한 주는 월요일부터라 {@code weekStart}는 월요일이고, 같은 주에 같은 주제는 한 번만 들어감
 * 승인은 글마다 따로 해서, 승인된 글만 보임
 * AI로 만든 글은 {@link PhilosophyStatus#PENDING}으로 저장하고, 팀원이 승인해야 사용자에게 보임
 *
 * 지난 글은 지우지 않음. 북마크 · 지난 글 목록에서 계속 볼 수 있어야 하고, 한 주에 몇 KB라 용량 부담도 없음
 */
@Entity
@Table(
        name = "philosophy_article",
        indexes = {
                // 이번 주 글(승인된 것 중 시작일이 오늘 이전인 최신 주) · 지난 글 목록
                @Index(name = "idx_philosophy_article_status_week", columnList = "status, week_start"),
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_philosophy_article_week_topic", columnNames = {"week_start", "topic_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhilosophyArticle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /** 이 글이 보이기 시작하는 주의 월요일 */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PhilosophyStatus status;

    /** 승인한 시각. 검토 전이면 NULL */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 철학자 글. 화면 탭 순서대로 */
    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<PhilosopherContent> contents = new ArrayList<>();

    /** 검토 전 상태로 만듦. 글은 {@link #addContent}로 붙임 */
    public static PhilosophyArticle createPending(Topic topic, LocalDate weekStart) {
        Objects.requireNonNull(weekStart, "weekStart");
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart 는 월요일이어야 합니다: " + weekStart);
        }
        PhilosophyArticle article = new PhilosophyArticle();
        article.topic = Objects.requireNonNull(topic, "topic");
        article.weekStart = weekStart;
        article.status = PhilosophyStatus.PENDING;
        return article;
    }

    /** 철학자 글을 맨 뒤에 붙임 */
    public void addContent(String philosopherName, List<PhilosophySection> sections) {
        contents.add(PhilosopherContent.create(this, philosopherName, contents.size(), sections));
    }

    /**
     * 글을 모두 지우고 검토 전으로 되돌림. 다시 생성할 때 새 글을 {@link #addContent}로 붙이기 전에 부름
     * 승인된 글이었어도 새 글은 다시 검토해야 하므로 승인을 취소함
     */
    public void clearContents() {
        contents.clear();
        status = PhilosophyStatus.PENDING;
        approvedAt = null;
    }

    /** 승인. 글이 없으면 승인할 수 없음 */
    public void approve(LocalDateTime now) {
        if (contents.isEmpty()) {
            throw new IllegalStateException("철학자 글이 없는 1분 철학은 승인할 수 없습니다");
        }
        status = PhilosophyStatus.APPROVED;
        approvedAt = Objects.requireNonNull(now, "now");
    }

    public boolean isApproved() {
        return status == PhilosophyStatus.APPROVED;
    }

    public List<PhilosopherContent> getContents() {
        return Collections.unmodifiableList(contents);
    }
}
