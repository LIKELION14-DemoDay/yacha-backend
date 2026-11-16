package likelion.yacha_backend.domain.topic.repository;

import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    /** 카테고리의 활성 주제 (id 오름차순). 랜덤 주제는 이 안에서 하위 카테고리 → 주제 순으로 고릅니다. */
    List<Topic> findAllByCategoryAndActiveTrueOrderByIdAsc(Category category);

    /** 친구 방 생성 시 카테고리에 활성 주제가 있는지 확인합니다. */
    boolean existsByCategoryAndActiveTrue(Category category);

    /** 활성 주제가 하나라도 있는 카테고리. 카테고리 구분 없는 랜덤 주제(봇전 찾기)는 이 중 하나를 먼저 고릅니다. */
    @Query("select distinct t.category from Topic t where t.active = true")
    List<Category> findCategoriesWithActiveTopics();

    /** 주제 파일에서 넣은 주제 (주제 키가 있는 것). */
    List<Topic> findAllByCodeIsNotNull();

    /** 방을 만들 때 고른 주제. 내린(비활성) 주제는 없는 것으로 봅니다. */
    Optional<Topic> findByIdAndActiveTrue(Long id);
}
