package likelion.yacha_backend.domain.topic.service;

import java.util.List;
import java.util.random.RandomGenerator;
import likelion.yacha_backend.domain.topic.dto.TopicResponse;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.exception.TopicErrorCode;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주제 뽑기 (명세 2-2).
 *
 * <p><b>하위 카테고리를 먼저</b> 고르고 그 안에서 주제를 고릅니다. 주제를 한꺼번에 놓고 고르면 주제가 많은
 * 하위 카테고리만 자주 나오기 때문입니다. 주제가 없는 하위 카테고리는 후보에서 빠집니다.
 *
 * <p>DB 의 {@code ORDER BY RAND()} 대신 후보를 읽어 서버에서 고릅니다. MySQL 과 테스트용 H2 가 똑같이 동작하고,
 * 하위 카테고리당 주제가 수십 개 수준이라 후보를 모두 읽어도 부담이 없습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final RandomGenerator random;

    /**
     * 카테고리 안에서 랜덤 주제 1개. "다음 주제로 넘어가기" 는 방금 본 주제 id 를 {@code excludeId} 로 넘깁니다.
     * 뽑을 주제가 없으면 {@code TOPIC_NOT_FOUND} 입니다.
     */
    public TopicResponse pickRandom(Category category, Long excludeId) {
        Subcategory subcategory = pick(topicRepository.findActiveSubcategories(category, excludeId));
        return TopicResponse.from(pick(topicRepository.findActiveTopics(subcategory, excludeId)));
    }

    /**
     * 후보 중 하나를 고릅니다. 후보가 없으면 {@code TOPIC_NOT_FOUND} 입니다.
     *
     * <p>두 번째 조회(주제)가 빌 수도 있습니다. 하위 카테고리를 고른 뒤 그 안의 마지막 주제가 비활성화되면
     * {@code READ COMMITTED}(H2) 에서는 두 조회가 다른 커밋을 봅니다.
     */
    private <T> T pick(List<T> candidates) {
        if (candidates.isEmpty()) {
            throw new BusinessException(TopicErrorCode.TOPIC_NOT_FOUND);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
}
