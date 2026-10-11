package likelion.yacha_backend.domain.session.dto;

import java.time.OffsetDateTime;
import likelion.yacha_backend.domain.session.entity.SessionStatus;

/**
 * 내가 참여 중인 세션. 새로고침 · 재접속으로 sessionId 를 잃었을 때 되찾는 데 씁니다.
 * {@code WAITING} 이면 대기 화면(또는 취소), {@code IN_PROGRESS} 면 토론방 구독 후 {@code /state}.
 *
 * @param expiresAt 대기 상한 시각 ({@code WAIT_PROMPT} 의 {@code expiresAt} 과 같은 값). 대기 중인 랜덤 방일 때만 있고, 아니면 null.
 *                  {@code WAIT_PROMPT} 는 연결이 끊긴 동안 사라지므로, 재접속한 방장은 이 값으로 남은 시간과 팝업을 바로 그립니다
 */
public record CurrentSessionResponse(Long sessionId, SessionStatus status, OffsetDateTime expiresAt) {
}
