package likelion.yacha_backend.domain.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import likelion.yacha_backend.domain.session.entity.SessionStatus;

/**
 * 내가 참여 중인 세션. 새로고침 시 대기 중인 친구 방의 초대 코드를 복원합니다.
 * 랜덤 방 및 진행 중인 방은 기존 sessionId, status 응답만 반환합니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CurrentSessionResponse(
        Long sessionId,
        SessionStatus status,
        String inviteCode,
        OffsetDateTime expiresAt
) {
    public CurrentSessionResponse(Long sessionId, SessionStatus status) {
        this(sessionId, status, null, null);
    }
}
