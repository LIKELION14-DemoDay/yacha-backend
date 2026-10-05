package likelion.yacha_backend.domain.topic.repository;

import java.util.List;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    /**
     * 카테고리 안에서 뽑을 주제가 있는 하위 카테고리. {@code excludeId} 주제는 없는 것으로 봅니다(null 이면 빼지 않음).
     * 순서는 하위 카테고리 이름순이라 같은 데이터면 같은 목록이 나옵니다.
     */
    @Query("""
            select distinct t.subcategory from Topic t
            where t.category = :category and t.active = true
              and (:excludeId is null or t.id <> :excludeId)
            order by t.subcategory
            """)
    List<Subcategory> findActiveSubcategories(@Param("category") Category category,
                                              @Param("excludeId") Long excludeId);

    /** 하위 카테고리 안에서 뽑을 주제 (id 오름차순). {@code excludeId} 는 뺍니다(null 이면 빼지 않음). */
    @Query("""
            select t from Topic t
            where t.subcategory = :subcategory and t.active = true
              and (:excludeId is null or t.id <> :excludeId)
            order by t.id
            """)
    List<Topic> findActiveTopics(@Param("subcategory") Subcategory subcategory,
                                 @Param("excludeId") Long excludeId);
}
