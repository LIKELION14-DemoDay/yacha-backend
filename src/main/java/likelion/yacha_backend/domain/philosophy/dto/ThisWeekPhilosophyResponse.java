package likelion.yacha_backend.domain.philosophy.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 1분 철학 첫 화면 — 이번 주 글
 *
 * @param weekStart 보여주는 주의 월요일. 공개된 글이 하나도 없으면 null
 * @param articles  카드. 공개된 글이 없으면 빈 목록
 */
public record ThisWeekPhilosophyResponse(LocalDate weekStart, List<PhilosophyCardResponse> articles) {
}
