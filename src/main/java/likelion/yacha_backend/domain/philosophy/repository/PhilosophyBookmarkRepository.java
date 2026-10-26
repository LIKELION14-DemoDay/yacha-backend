package likelion.yacha_backend.domain.philosophy.repository;

import java.util.List;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhilosophyBookmarkRepository extends JpaRepository<PhilosophyBookmark, Long> {

    boolean existsByUser_IdAndArticle_Id(Long userId, Long articleId);

    /** 글들 중 북마크한 글 id */
    @Query("""
            select b.article.id from PhilosophyBookmark b
            where b.user.id = :userId and b.article.id in :articleIds
            """)
    List<Long> findBookmarkedArticleIds(@Param("userId") Long userId, @Param("articleIds") List<Long> articleIds);
}
