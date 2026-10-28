package likelion.yacha_backend.domain.philosophy.service;

import java.util.List;

/**
 * 1분 철학 글 생성 결과
 *
 * @param createdIds 새로 만든 글 id (검토 전)
 * @param failed     만들지 못한 편수. 관리자 API로 모자란 만큼 다시 생성하면 됨
 */
public record PhilosophyGenerationResult(List<Long> createdIds, int failed) {

    public PhilosophyGenerationResult {
        createdIds = List.copyOf(createdIds);
    }
}
