package likelion.yacha_backend.domain.philosophy.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1분 철학의 철학자 한 명의 글. 화면의 탭 하나
 *
 * 철학자 이름은 AI가 고른 이름을 그대로 저장함 (아리스토텔레스 · 플라톤 등)
 * 회원의 철학자 유형 8개와는 따로임
 *
 * {@link PhilosophyArticle}를 통해서만 만들고 지움
 */
@Entity
@Table(name = "philosopher_content")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhilosopherContent {

    /** 철학자 이름 글자 수 상한. 컬럼 길이와 같음 */
    public static final int PHILOSOPHER_NAME_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "philosophy_article_id", nullable = false)
    private PhilosophyArticle article;

    @Column(name = "philosopher_name", nullable = false, length = PHILOSOPHER_NAME_MAX_LENGTH)
    private String philosopherName;

    /** 탭 순서 (0부터) */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 소제목 + 내용 묶음. 위에서부터 차례로 */
    @ElementCollection
    @CollectionTable(
            name = "philosopher_content_section",
            joinColumns = @JoinColumn(name = "philosopher_content_id", nullable = false)
    )
    @OrderColumn(name = "sort_order", nullable = false)
    private List<PhilosophySection> sections = new ArrayList<>();

    static PhilosopherContent create(PhilosophyArticle article, String philosopherName, int sortOrder,
                                     List<PhilosophySection> sections) {
        Objects.requireNonNull(sections, "sections");
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("철학자 글에는 소제목 + 내용이 하나 이상 있어야 합니다");
        }
        PhilosopherContent content = new PhilosopherContent();
        content.article = Objects.requireNonNull(article, "article");
        content.philosopherName = Objects.requireNonNull(philosopherName, "philosopherName");
        content.sortOrder = sortOrder;
        content.sections.addAll(sections);
        return content;
    }

    public List<PhilosophySection> getSections() {
        return Collections.unmodifiableList(sections);
    }
}
