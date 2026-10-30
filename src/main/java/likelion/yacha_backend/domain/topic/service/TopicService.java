package likelion.yacha_backend.domain.topic.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.random.RandomGenerator;
import likelion.yacha_backend.domain.topic.dto.TopicResponse;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
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
 * <p>DB 의 {@code ORDER BY RAND()} 대신 카테고리의 활성 주제를 한 번에 읽어 서버에서 고릅니다. MySQL 과 테스트용 H2 가
 * 똑같이 동작하고, 카테고리당 주제가 수십 개 수준이라 모두 읽어도 부담이 없습니다. 한 번에 읽으므로 하위 카테고리와
 * 주제를 따로 조회하는 사이에 주제가 내려가 후보가 비는 일도 없습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final RandomGenerator random;

    /**
     * 카테고리 안에서 랜덤 주제 1개. "다음 주제로 넘어가기" 는 그 화면에서 본 주제 id 를 모두 {@code excludeIds} 로
     * 넘깁니다. 다른 카테고리 · 없는 id 는 무시합니다.
     *
     * @throws BusinessException 카테고리에 활성 주제가 없으면 {@code TOPIC_NOT_FOUND}, 있지만 모두 뺐으면
     *                           {@code ALL_TOPICS_SEEN} (프론트가 "모든 주제를 봤어요" 를 띄우고 처음부터 다시 요청)
     */
    public TopicResponse pickRandom(Category category, Collection<Long> excludeIds) {
        List<Topic> topics = topicRepository.findAllByCategoryAndActiveTrueOrderByIdAsc(category);
        if (topics.isEmpty()) {
            throw new BusinessException(TopicErrorCode.TOPIC_NOT_FOUND);
        }
        Set<Long> excluded = new HashSet<>(excludeIds);
        // 하위 카테고리 이름순으로 묶어, 같은 데이터면 같은 후보 순서가 나오게 합니다.
        Map<Subcategory, List<Topic>> candidates = topics.stream()
                .filter(topic -> !excluded.contains(topic.getId()))
                .collect(Collectors.groupingBy(Topic::getSubcategory,
                        () -> new TreeMap<>(Comparator.comparing(Subcategory::name)), Collectors.toList()));
        if (candidates.isEmpty()) {
            throw new BusinessException(TopicErrorCode.ALL_TOPICS_SEEN);
        }
        List<Subcategory> subcategories = List.copyOf(candidates.keySet());
        List<Topic> inSubcategory = candidates.get(subcategories.get(random.nextInt(subcategories.size())));
        return TopicResponse.from(inSubcategory.get(random.nextInt(inSubcategory.size())));
    }
}
