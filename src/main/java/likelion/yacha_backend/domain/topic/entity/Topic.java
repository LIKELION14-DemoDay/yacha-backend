package likelion.yacha_backend.domain.topic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Objects;
import likelion.yacha_backend.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 토론 주제 (명세 1-2). 카테고리별 주제 풀이고, 랜덤 야차 · 봇전 모두 여기서 뽑습니다.
 *
 * <p>주제 질문은 팀이 직접 작성해 {@code topics/topics.json} 에 적고, 서버가 뜰 때 DB 를 그 파일과 맞춥니다 (#54). {@code statement} 는 주제 화면 · 대기 목록에 뜨는 질문,
 * {@code agreeText} · {@code disagreeText} 는 입장 문구로 작성 화면 · 채팅 상단("찬성 VS 반대")에 씁니다.
 * 화면에 긴 문장을 다 보여 줄 수 없어 답은 예 / 아니오로만 받으므로, 주제 파일의 주제는 입장 문구가 {@value #YES} · {@value #NO} 입니다.
 *
 * <p>상위 카테고리는 하위 카테고리로 정해지지만, 카테고리 단위 조회 · 인덱스를 위해 같이 저장합니다.
 */
@Entity
@Table(
        name = "topic",
        indexes = {
                // 카테고리 안에서 주제가 있는 하위 카테고리를 찾는다
                @Index(name = "idx_topic_category", columnList = "category, is_active"),
                // 하위 카테고리 안에서 랜덤으로 뽑는다
                @Index(name = "idx_topic_subcategory", columnList = "subcategory, is_active"),
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic extends BaseTimeEntity {

    /** 입장 문구 글자 수 상한. 컬럼 길이와 같습니다. */
    public static final int STANCE_TEXT_MAX_LENGTH = 100;

    /** 주제 키 글자 수 상한. 컬럼 길이와 같습니다. */
    public static final int CODE_MAX_LENGTH = 50;

    /** 주제 파일 주제의 찬성 입장 문구. */
    public static final String YES = "예";

    /** 주제 파일 주제의 반대 입장 문구. */
    public static final String NO = "아니오";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 주제 파일({@code topics/topics.json})의 주제 키. 문구를 고쳐도 같은 주제로 알아보는 데 씁니다.
     * 파일 밖에서 넣은 주제는 null 이고, 파일과 맞출 때 건드리지 않습니다.
     */
    @Column(unique = true, length = CODE_MAX_LENGTH)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Subcategory subcategory;

    /** 동의 / 비동의로 답하는 질문형 명제. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    /** 찬성(예) 입장 문구. */
    @Column(name = "agree_text", nullable = false, length = STANCE_TEXT_MAX_LENGTH)
    private String agreeText;

    /** 반대(아니오) 입장 문구. */
    @Column(name = "disagree_text", nullable = false, length = STANCE_TEXT_MAX_LENGTH)
    private String disagreeText;

    /** 랜덤 추첨 대상 여부. 내린 주제는 false 입니다. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    public static Topic create(Subcategory subcategory, String statement, String agreeText, String disagreeText) {
        Topic topic = new Topic();
        topic.subcategory = Objects.requireNonNull(subcategory, "subcategory");
        topic.category = subcategory.getCategory();
        topic.statement = Objects.requireNonNull(statement, "statement");
        topic.agreeText = Objects.requireNonNull(agreeText, "agreeText");
        topic.disagreeText = Objects.requireNonNull(disagreeText, "disagreeText");
        topic.active = true;
        return topic;
    }

    /** 주제 파일의 주제. 입장 문구는 {@value #YES} · {@value #NO} 입니다. */
    public static Topic createSeed(String code, Subcategory subcategory, String statement, boolean active) {
        Topic topic = create(subcategory, statement, YES, NO);
        topic.code = Objects.requireNonNull(code, "code");
        topic.active = active;
        return topic;
    }

    /**
     * 주제 파일의 내용으로 바꿉니다. 이미 이 주제로 한 게임의 기록에도 바뀐 질문이 보입니다.
     *
     * @return 바뀐 값이 있으면 true
     */
    public boolean syncWith(Subcategory subcategory, String statement, boolean active) {
        boolean changed = this.subcategory != subcategory || !this.statement.equals(statement)
                || !YES.equals(this.agreeText) || !NO.equals(this.disagreeText) || this.active != active;
        this.subcategory = Objects.requireNonNull(subcategory, "subcategory");
        this.category = subcategory.getCategory();
        this.statement = Objects.requireNonNull(statement, "statement");
        this.agreeText = YES;
        this.disagreeText = NO;
        this.active = active;
        return changed;
    }

    /** 랜덤 추첨에서 뺍니다. 이미 이 주제로 만든 방에는 영향이 없습니다. */
    public void deactivate() {
        this.active = false;
    }
}
