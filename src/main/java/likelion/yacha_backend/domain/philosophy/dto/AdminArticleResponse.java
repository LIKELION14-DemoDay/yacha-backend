package likelion.yacha_backend.domain.philosophy.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyStatus;

/**
 * 관리자 검토용 1분 철학 글. 철학자 글 내용까지 모두 보여줌
 *
 * @param id          글 id. 승인 · 다시 생성 · 빼기에 씀
 * @param weekStart   공개되는 주의 월요일
 * @param status      {@code PENDING} 검토 전 / {@code APPROVED} 승인됨
 * @param approvedAt  승인한 시각. 검토 전이면 null
 * @param topic       주제
 * @param philosophers 철학자 글. 탭 순서대로
 */
public record AdminArticleResponse(
        Long id,
        LocalDate weekStart,
        PhilosophyStatus status,
        LocalDateTime approvedAt,
        PhilosophyTopicResponse topic,
        List<PhilosopherResponse> philosophers
) {

    public static AdminArticleResponse from(PhilosophyArticle article) {
        return new AdminArticleResponse(article.getId(), article.getWeekStart(), article.getStatus(),
                article.getApprovedAt(), PhilosophyTopicResponse.from(article.getTopic()),
                article.getContents().stream().map(PhilosopherResponse::from).toList());
    }
}
