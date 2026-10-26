package likelion.yacha_backend.domain.philosophy.dto;

import java.time.LocalDate;
import java.util.List;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;

/**
 * 1분 철학 글 상세
 *
 * @param id           글 id
 * @param weekStart    공개된 주의 월요일
 * @param topic        주제 (해시태그 · 질문)
 * @param philosophers 철학자 글. 탭 순서대로
 * @param memo         내 메모. 없으면 null
 * @param bookmarked   내가 북마크했는가
 */
public record PhilosophyDetailResponse(
        Long id,
        LocalDate weekStart,
        PhilosophyTopicResponse topic,
        List<PhilosopherResponse> philosophers,
        String memo,
        boolean bookmarked
) {

    public static PhilosophyDetailResponse of(PhilosophyArticle article, String memo, boolean bookmarked) {
        return new PhilosophyDetailResponse(article.getId(), article.getWeekStart(),
                PhilosophyTopicResponse.from(article.getTopic()),
                article.getContents().stream().map(PhilosopherResponse::from).toList(),
                memo, bookmarked);
    }
}
