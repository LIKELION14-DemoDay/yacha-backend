package likelion.yacha_backend.domain.topic.seed;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 의 주제를 주제 파일과 맞춥니다. 파일이 기준입니다.
 *
 * <ul>
 *   <li>파일에만 있는 주제는 넣습니다</li>
 *   <li>둘 다 있으면 파일 내용으로 고칩니다 (주제 키로 같은 주제인지 봅니다)</li>
 *   <li>DB 에만 있는 주제는 지우지 않고 내립니다. 이미 그 주제로 한 게임이 참조하기 때문입니다</li>
 *   <li>주제 키가 없는 주제(파일 밖에서 넣은 것)는 건드리지 않습니다</li>
 * </ul>
 *
 * <p>파일이 비었는데 DB 에 파일 주제가 있으면 맞추지 않고 실패합니다. 병합 실수 등으로 파일이 비면 모든 주제가 내려가
 * 방 생성 · 자동 제안 · 봇전이 전부 막히기 때문입니다. 서버가 뜨지 않아 배포에서 바로 드러납니다.
 */
@Service
@RequiredArgsConstructor
public class TopicSeedService {

    private final TopicRepository topicRepository;

    @Transactional
    public Result sync(List<TopicSeed> seeds) {
        Map<String, Topic> existing = topicRepository.findAllByCodeIsNotNull().stream()
                .collect(Collectors.toMap(Topic::getCode, Function.identity()));
        if (seeds.isEmpty() && !existing.isEmpty()) {
            throw new IllegalStateException("주제 파일이 비었는데 DB 에 파일 주제가 " + existing.size() + "건 있습니다");
        }
        int created = 0;
        int updated = 0;
        for (TopicSeed seed : seeds) {
            Topic topic = existing.remove(seed.code());
            if (topic == null) {
                topicRepository.save(Topic.createSeed(seed.code(), seed.subcategory(), seed.statement(),
                        seed.isActive()));
                created++;
            } else if (topic.syncWith(seed.subcategory(), seed.statement(), seed.isActive())) {
                updated++;
            }
        }
        int deactivated = 0;
        for (Topic removed : existing.values()) {
            if (removed.isActive()) {
                removed.deactivate();
                deactivated++;
            }
        }
        return new Result(created, updated, deactivated);
    }

    /**
     * @param created     새로 넣은 주제 수
     * @param updated     내용을 고친 주제 수 (내리기 · 다시 올리기 포함)
     * @param deactivated 파일에서 빠져 내린 주제 수
     */
    public record Result(int created, int updated, int deactivated) {
    }
}
