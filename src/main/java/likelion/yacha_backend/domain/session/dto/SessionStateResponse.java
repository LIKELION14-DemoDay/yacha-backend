package likelion.yacha_backend.domain.session.dto;

import java.time.OffsetDateTime;
import java.util.List;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.ParticipantRole;
import likelion.yacha_backend.domain.session.entity.ParticipantType;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.SessionMode;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.topic.entity.Category;

/**
 * {@code GET /sessions/{id}/state} — 세션 정보와 현재 구간 (재접속 · 새로고침 · 관전 입장용).
 *
 * <p>세션 상세({@code GET /sessions/{id}})도 이 응답으로 대신합니다. DB 에 남는 값이 주제 · 방 상태 · 참가자뿐이라
 * 따로 둘 이유가 없습니다.
 *
 * <p><b>승패와 사용자 id 는 넣지 않습니다.</b> 승패는 본인만 전투 기록에서 보고, 관전자에게 사용자 id 를 노출하지 않습니다.
 *
 * @param phase         현재 구간. 진행 중일 때만 있고, 대기 · 종료 · 취소면 null
 * @param endsAt        현재 구간이 끝나는 시각. {@code JUDGING} 이거나 진행 중이 아니면 null
 * @param serverNow     서버 시각. 프론트는 이 값과의 차이로 남은 시간을 계산합니다
 * @param myParticipantId 내 참가자 id. 관전자면 null
 * @param participants  참가자 목록. 채팅 이벤트의 {@code senderId} 가 어느 쪽인지 여기서 찾습니다
 */
public record SessionStateResponse(
        Long sessionId,
        SessionStatus status,
        RoomType roomType,
        SessionMode mode,
        Category category,
        Long topicId,
        DebatePhase phase,
        OffsetDateTime startedAt,
        OffsetDateTime endsAt,
        OffsetDateTime serverNow,
        Long myParticipantId,
        List<ParticipantResponse> participants
) {

    /**
     * @param participantId 참가자 id (채팅의 {@code senderId})
     * @param type          {@code USER} / {@code AI}
     * @param role          방장 {@code INITIATOR} / 상대 {@code OPPONENT}
     * @param stance        동의 / 비동의. 친구 방은 친구가 들어오기 전까지 null
     * @param submitted     지금 작성 구간(구간 종료 뒤 3초 유예 포함)에 주장 · 반론을 제출했는지.
     *                      작성 구간이 아니면 null. 재접속해도 "상대방이 아직 작성중입니다" 화면을 맞추는 용도라 내용은 없습니다
     */
    public record ParticipantResponse(
            Long participantId,
            ParticipantType type,
            ParticipantRole role,
            Stance stance,
            Boolean submitted
    ) {
    }
}
