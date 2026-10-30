package likelion.yacha_backend.domain.topic.repository;

import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    /** 카테고리의 활성 주제 (id 오름차순). 랜덤 주제는 이 안에서 하위 카테고리 → 주제 순으로 고릅니다. */
    List<Topic> findAllByCategoryAndActiveTrueOrderByIdAsc(Category category);

    /** 주제 파일에서 넣은 주제 (주제 키가 있는 것). */
    List<Topic> findAllByCodeIsNotNull();

    /** 방을 만들 때 고른 주제. 내린(비활성) 주제는 없는 것으로 봅니다. */
    Optional<Topic> findByIdAndActiveTrue(Long id);
}
