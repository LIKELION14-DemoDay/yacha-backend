package likelion.yacha_backend.domain.philosophy.repository;

import java.time.LocalDate;
import java.util.List;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhilosophyArticleRepository extends JpaRepository<PhilosophyArticle, Long> {

    long countByWeekStart(LocalDate weekStart);

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
