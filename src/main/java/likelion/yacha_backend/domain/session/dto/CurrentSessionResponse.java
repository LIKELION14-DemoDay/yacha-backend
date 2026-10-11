package likelion.yacha_backend.domain.session.dto;

import likelion.yacha_backend.domain.session.entity.SessionStatus;

/**
 * 내가 참여 중인 세션. 새로고침 · 재접속으로 sessionId 를 잃었을 때 되찾는 데 씁니다.
 * {@code WAITING} 이면 대기 화면(또는 취소), {@code IN_PROGRESS} 면 토론방 구독 후 {@code /state}.
 */
public record CurrentSessionResponse(Long sessionId, SessionStatus status) {
}
