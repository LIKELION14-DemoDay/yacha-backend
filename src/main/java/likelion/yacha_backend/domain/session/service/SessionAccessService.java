package likelion.yacha_backend.domain.session.service;

import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.SessionMode;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토론방을 누가 볼 수 있는지 (명세 2-4 구독 권한).
 *
 * <p>토론방 구독 · 메시지 조회 · 현재 상태 조회가 모두 이 규칙을 씁니다.
 * <ul>
 *   <li><b>참가자</b>는 자기 세션을 언제나 볼 수 있습니다.</li>
 *   <li><b>관전자</b>는 진행 중({@code IN_PROGRESS})인 <b>랜덤 사람전</b>({@code RANDOM} + {@code HUMAN})만 볼 수 있습니다.</li>
 *   <li>친구 방과 봇전은 <b>관전 없이 항상 비공개</b>입니다. 봇전은 대기 중 AI 전환 · 자동 봇전 모두
 *       {@code room_type} 이 {@code RANDOM} 으로 남으므로 {@code mode} 로 걸러야 합니다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionAccessService {

    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;

    public DebateSession getSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(SessionErrorCode.SESSION_NOT_FOUND));
    }

    /** 참가자이거나 관전할 수 있는 방이면 true. 없는 세션이면 false 입니다. */
    public boolean canView(Long sessionId, Long userId) {
        return sessionRepository.findById(sessionId)
                .map(session -> canView(session, userId))
                .orElse(false);
    }

    /** 볼 수 없으면 {@code NOT_PARTICIPANT} 입니다. 관전자가 비공개 방을 볼 때도 같은 코드입니다 (명세 2-9). */
    public void checkViewer(DebateSession session, Long userId) {
        if (!canView(session, userId)) {
            throw new BusinessException(SessionErrorCode.NOT_PARTICIPANT);
        }
    }

    /** 관전(보기만)을 허용하는 방인가. 진행 중인 랜덤 사람전만 허용합니다. */
    public static boolean isSpectatable(DebateSession session) {
        return session.isInProgress()
                && session.getRoomType() == RoomType.RANDOM
                && session.getMode() == SessionMode.HUMAN;
    }

    private boolean canView(DebateSession session, Long userId) {
        return participantRepository.existsBySession_IdAndUser_Id(session.getId(), userId)
                || isSpectatable(session);
    }
}
