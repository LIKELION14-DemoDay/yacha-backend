package likelion.yacha_backend.domain.philosophy.repository;

import java.time.LocalDate;
import java.util.List;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhilosophyArticleRepository extends JpaRepository<PhilosophyArticle, Long> {

    long countByWeekStart(LocalDate weekStart);

    List<PhilosophyArticle> findAllByWeekStartOrderByIdAsc(LocalDate weekStart);

    /** 시작일이 {@code today} 이전인 주 중, 상태가 {@code status}인 글이 있는 가장 최근 주. 없으면 null */
    @Query("select max(a.weekStart) from PhilosophyArticle a where a.status = :status and a.weekStart <= :today")
    LocalDate findLatestWeekStart(@Param("status") PhilosophyStatus status, @Param("today") LocalDate today);

    /** 그 주의 상태가 {@code status}인 글, 만든 순서대로. 주제를 함께 읽음 */
    @Query("""
            select a from PhilosophyArticle a join fetch a.topic
            where a.weekStart = :weekStart and a.status = :status
            order by a.id
            """)
    List<PhilosophyArticle> findAllByWeekWithTopic(@Param("weekStart") LocalDate weekStart,
                                                   @Param("status") PhilosophyStatus status);

    /** {@code before} 주보다 앞선 주의 상태가 {@code status}인 글, 최신 주부터. 주제를 함께 읽음 */
    @Query("""
            select a from PhilosophyArticle a join fetch a.topic
            where a.weekStart < :before and a.status = :status
            order by a.weekStart desc, a.id
            """)
    Slice<PhilosophyArticle> findAllBeforeWithTopic(@Param("before") LocalDate before,
                                                    @Param("status") PhilosophyStatus status, Pageable pageable);

    /** 글들의 철학자 이름. 글 id · 탭 순서대로 */
    @Query("""
            select new likelion.yacha_backend.domain.philosophy.repository.ArticlePhilosopherName(c.article.id, c.philosopherName)
            from PhilosopherContent c
            where c.article.id in :articleIds
            order by c.article.id, c.sortOrder
            """)
    List<ArticlePhilosopherName> findPhilosopherNames(@Param("articleIds") List<Long> articleIds);

    /** 그 주에 이미 쓴 주제 id */
    @Query("select a.topic.id from PhilosophyArticle a where a.weekStart = :weekStart")
    List<Long> findTopicIdsByWeekStart(@Param("weekStart") LocalDate weekStart);

    /** 아직 1분 철학에 쓰지 않은 활성 주제 id (오름차순) */
    @Query("""
            select t.id from Topic t
            where t.active = true
              and not exists (select 1 from PhilosophyArticle a where a.topic = t)
            order by t.id
            """)
    List<Long> findUnusedActiveTopicIds();

    /** 활성 주제 id 전체 (오름차순). 안 쓴 주제가 모자랄 때 씀 */
    @Query("select t.id from Topic t where t.active = true order by t.id")
    List<Long> findActiveTopicIds();
}
