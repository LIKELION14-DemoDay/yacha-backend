package likelion.yacha_backend.domain.philosophy.dto;

import java.time.LocalDate;
import java.util.List;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;

/**
 * 1분 철학 목록의 카드 한 장. 해시태그 · 질문 · 철학자 이름
 *
 * @param id           글 id. 상세 · 메모 · 북마크에 씀
 * @param weekStart    공개된 주의 월요일. 지난 글 목록을 주별로 묶을 때 씀
 * @param topic        주제
 * @param philosophers 철학자 이름. 탭 순서대로
 * @param bookmarked   내가 북마크했는가
 */
public record PhilosophyCardResponse(
        Long id,
        LocalDate weekStart,
        PhilosophyTopicResponse topic,
        List<String> philosophers,
        boolean bookmarked
) {

    public static PhilosophyCardResponse of(PhilosophyArticle article, List<String> philosophers, boolean bookmarked) {
        return new PhilosophyCardResponse(article.getId(), article.getWeekStart(),
                PhilosophyTopicResponse.from(article.getTopic()), philosophers, bookmarked);
    }
}
