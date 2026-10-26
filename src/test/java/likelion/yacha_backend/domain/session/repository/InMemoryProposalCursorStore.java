package likelion.yacha_backend.domain.session.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 테스트 전용 인메모리 구현. 만료는 없고, 만료된 경우는 {@link #delete} 로 흉내 냄 */
@Repository
@Profile("test")
public class InMemoryProposalCursorStore implements ProposalCursorStore {

    private final Map<Long, ProposalCursor> cursors = new ConcurrentHashMap<>();

    @Override
    public void save(Long userId, ProposalCursor cursor) {
        cursors.put(userId, cursor);
    }

    @Override
    public Optional<ProposalCursor> find(Long userId) {
        return Optional.ofNullable(cursors.get(userId));
    }

    @Override
    public void delete(Long userId) {
        cursors.remove(userId);
    }
}
