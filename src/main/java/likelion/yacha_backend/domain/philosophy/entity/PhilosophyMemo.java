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
import java.util.Objects;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1분 철학 메모. 회원 한 명이 글(주제) 한 편마다 하나씩 가짐
 *
 * 다시 저장하면 새로 만들지 않고 내용을 덮어씀
 */
@Entity
@Table(
        name = "philosophy_memo",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_philosophy_memo_user_article", columnNames = {"user_id", "philosophy_article_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhilosophyMemo extends BaseTimeEntity {

    /** 메모 글자 수 상한. 컬럼 길이와 같음 */
    public static final int CONTENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "philosophy_article_id", nullable = false)
    private PhilosophyArticle article;

    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    public static PhilosophyMemo create(User user, PhilosophyArticle article, String content) {
        PhilosophyMemo memo = new PhilosophyMemo();
        memo.user = Objects.requireNonNull(user, "user");
        memo.article = Objects.requireNonNull(article, "article");
        memo.content = Objects.requireNonNull(content, "content");
        return memo;
    }

    public void updateContent(String content) {
        this.content = Objects.requireNonNull(content, "content");
    }
}
