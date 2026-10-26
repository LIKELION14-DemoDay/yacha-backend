package likelion.yacha_backend.domain.philosophy.dto;

import java.time.LocalDate;

/**
 * 생성을 시작한 주. 몇 분 뒤 {@code GET /admin/philosophy?weekStart=}로 결과를 확인
 *
 * @param weekStart 글을 만드는 주의 월요일
 */
public record AdminGenerationResponse(LocalDate weekStart) {
}
