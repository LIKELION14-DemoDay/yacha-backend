package likelion.yacha_backend.domain.philosophy.repository;

import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyBookmark;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhilosophyBookmarkRepository extends JpaRepository<PhilosophyBookmark, Long> {

    boolean existsByUser_IdAndArticle_Id(Long userId, Long articleId);

    Optional<PhilosophyBookmark> findByUser_IdAndArticle_Id(Long userId, Long articleId);

    /** 내 북마크, 북마크한 순서 최신부터. 글 · 주제를 함께 읽음 */
    @Query("""
            select b from PhilosophyBookmark b join fetch b.article a join fetch a.topic
            where b.user.id = :userId
            order by b.createdAt desc, b.id desc
            """)
    Slice<PhilosophyBookmark> findMine(@Param("userId") Long userId, Pageable pageable);

    /** 글들 중 북마크한 글 id */
    @Query("""
            select b.article.id from PhilosophyBookmark b
            where b.user.id = :userId and b.article.id in :articleIds
            """)
    List<Long> findBookmarkedArticleIds(@Param("userId") Long userId, @Param("articleIds") List<Long> articleIds);
}
