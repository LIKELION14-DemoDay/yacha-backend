package likelion.yacha_backend.domain.philosophy.dto;

import java.util.List;

/**
 * 카드 목록 한 페이지 (지난 글 · 내 북마크)
 *
 * @param articles 카드
 * @param hasNext  다음 페이지가 있는가
 */
public record PhilosophyCardsResponse(List<PhilosophyCardResponse> articles, boolean hasNext) {
}
