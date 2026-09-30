package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import likelion.yacha_backend.domain.session.dto.GameMessageResponse;
import likelion.yacha_backend.domain.session.dto.SessionStateResponse;
import likelion.yacha_backend.domain.session.dto.SessionStateResponse.ParticipantResponse;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.PhaseState;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.util.DateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토론방 조회 — 놓친 메시지 · 현재 상태. 볼 수 있는 사람은 구독과 같습니다 ({@link SessionAccessService}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionQueryService {

    private final SessionAccessService sessionAccessService;
    private final DebateParticipantRepository participantRepository;
    private final GameRegistry gameRegistry;
    private final Clock clock;

    /**
     * {@code seqNo > afterSeq} 인 메시지. 재접속 보충과 늦게 들어온 관전자용입니다.
     *
     * <p>채팅은 게임 메모리에만 있으므로 게임이 없으면(시작 전 · 정리됨 · 서버 재시작) 조회되지 않습니다.
     *
     * <p>게임이 끝난 뒤에도 판정 LLM 이 읽도록 메시지가 메모리에 남아 있습니다. 게임 존재 여부만 보면
     * 종료된 토론의 채팅을 참가자가 다시 받아 갈 수 있으므로, 세션이 진행 중일 때만 돌려줍니다.
     */
    public List<GameMessageResponse> getMessages(Long sessionId, Long userId, long afterSeq) {
        DebateSession session = sessionAccessService.getSession(sessionId);
        sessionAccessService.checkViewer(session, userId);
        if (!session.isInProgress()) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_IN_PROGRESS);
        }

        ZoneId zone = clock.getZone();
        return gameRegistry.find(sessionId)
                .orElseThrow(() -> new BusinessException(SessionErrorCode.SESSION_NOT_IN_PROGRESS))
                .messagesAfter(afterSeq).stream()
                .map(message -> GameMessageResponse.from(message, zone))
                .toList();
    }

    public SessionStateResponse getState(Long sessionId, Long userId) {
        DebateSession session = sessionAccessService.getSession(sessionId);
        sessionAccessService.checkViewer(session, userId);

        LocalDateTime now = LocalDateTime.now(clock);
        ZoneId zone = clock.getZone();
        List<DebateParticipant> participants = participantRepository.findAllBySession_Id(sessionId);

        DebatePhase phase = null;
        LocalDateTime endsAt = null;
        if (session.isInProgress() && session.getStartedAt() != null) {
            PhaseState state = DebatePhase.at(session.getStartedAt(), now);
            phase = state.phase();
            endsAt = state.endsAt();
        }

        return new SessionStateResponse(
                session.getId(),
                session.getStatus(),
                session.getRoomType(),
                session.getMode(),
                session.getCategory(),
                session.getTopicId(),
                phase,
                DateTimes.withOffset(session.getStartedAt(), zone),
                DateTimes.withOffset(endsAt, zone),
                DateTimes.withOffset(now, zone),
                myParticipantId(participants, userId),
                participants.stream()
                        .map(p -> new ParticipantResponse(p.getId(), p.getParticipantType(), p.getRole(), p.getStance()))
                        .toList());
    }

    private Long myParticipantId(List<DebateParticipant> participants, Long userId) {
        return participants.stream()
                .filter(p -> !p.isAi() && userId.equals(p.getUser().getId()))
                .map(DebateParticipant::getId)
                .findFirst()
                .orElse(null);
    }
}
