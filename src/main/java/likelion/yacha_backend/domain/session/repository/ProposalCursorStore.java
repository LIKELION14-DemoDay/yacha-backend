package likelion.yacha_backend.domain.session.repository;

import java.util.Optional;

/**
 * 사용자별 자동 제안 순환 위치. 짧게만 두고(Redis TTL), 입장에 성공하거나 제안을 새로 시작하면 지웁니다.
 * 운영은 Redis 구현, 테스트는 메모리 구현을 씁니다.
 */
public interface ProposalCursorStore {

    void save(Long userId, ProposalCursor cursor);

    /** 저장한 적이 없거나 만료됐으면 비어 있습니다. */
    Optional<ProposalCursor> find(Long userId);

    void delete(Long userId);
}
