package likelion.yacha_backend.domain.session.repository;

import java.time.LocalDateTime;

/**
 * 대기 중인 랜덤 방 — 서버 재시작 뒤 대기 타이머를 다시 등록할 때 씁니다.
 *
 * @param hostUserId 방장. 게스트 정리로 계정이 지워졌으면 null
 */
public record WaitingRoom(Long sessionId, Long hostUserId, LocalDateTime createdAt) {
}
