package likelion.yacha_backend.domain.heart.dto;

import java.time.OffsetDateTime;

/**
 * {@code GET /hearts/me} — 내 하트.
 *
 * @param hearts       지금 남은 하트
 * @param max          최대 하트 수
 * @param nextChargeAt 다음 하트가 차는 시각. 가득 차 있으면 null
 */
public record HeartResponse(int hearts, int max, OffsetDateTime nextChargeAt) {
}
