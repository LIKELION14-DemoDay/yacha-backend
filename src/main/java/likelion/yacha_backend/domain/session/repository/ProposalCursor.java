package likelion.yacha_backend.domain.session.repository;

import java.util.Objects;
import likelion.yacha_backend.domain.topic.entity.Category;

/**
 * 자동 제안의 순환 위치 (명세 2-3-2). 사용자가 고른 카테고리부터 몇 번째 카테고리를 볼 차례인지입니다.
 *
 * @param start      처음 고른 카테고리. 한 바퀴를 다 돌면 이 카테고리에서 새 주제를 뽑습니다
 * @param nextOffset 다음에 볼 카테고리가 {@code start} 에서 몇 칸 뒤인지 (0 = {@code start})
 */
public record ProposalCursor(Category start, int nextOffset) {

    public ProposalCursor {
        Objects.requireNonNull(start, "start");
    }
}
