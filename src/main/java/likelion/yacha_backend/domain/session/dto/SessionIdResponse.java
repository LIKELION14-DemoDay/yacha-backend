
package likelion.yacha_backend.domain.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionIdResponse(
        Long sessionId,
        String inviteCode,
        OffsetDateTime expiresAt
) {
    // 기존 랜덤 방, 입장, 취소 응답과 호환
    public SessionIdResponse(Long sessionId) {
        this(sessionId, null, null);
    }
}
