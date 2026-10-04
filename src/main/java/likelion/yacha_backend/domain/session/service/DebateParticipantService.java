package likelion.yacha_backend.domain.session.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참가 기록에서 사용자 연결을 끊습니다. 인증 도메인(게스트 정리 · 승격)은 참가 기록을 직접 고치지 않고 이 서비스를 거칩니다.
 *
 * <p>연결을 끊어도 참가 기록과 상대의 기록은 남고 {@code user_id} 만 비어, AI 참가자처럼 사용자가 없는 기록이 됩니다.
 * 그래서 참가 기록을 사용자와 비교할 때는 {@code getUser()} 대신 {@link DebateParticipant#isUser} 를 씁니다.
 *
 * <p>호출하는 쪽의 트랜잭션에 함께 묶입니다. 연결을 끊은 뒤 계정을 지우거나 승격하는 일이 한 번에 반영돼야 하기 때문입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DebateParticipantService {

    /** 연결을 그대로 둘 세션 상태. 끊으면 지금 하고 있는 게임에서 참가자로 인정되지 않습니다. */
    private static final Set<SessionStatus> ACTIVE_STATUSES = EnumSet.of(SessionStatus.WAITING, SessionStatus.IN_PROGRESS);

    private final DebateParticipantRepository participantRepository;

    /** 지울 게스트 계정들의 참가 기록에서 연결을 끊습니다. 계정을 지우기 전에 불러야 합니다 (users FK). */
    public void detachUsers(Collection<Long> userIds) {
        participantRepository.detachUsers(userIds);
    }

    /**
     * 끝났거나 취소된 세션의 참가 기록에서만 이 사용자 연결을 끊습니다. 게스트가 회원으로 승격할 때 부릅니다.
     * 비회원 게임 결과는 전적에 넣지 않기로 해서, 게스트 때 한 경기가 승격 뒤 회원 전적에 섞이지 않게 합니다.
     * 대기 · 진행 중인 세션은 그대로 둡니다.
     */
    public void detachFromEndedSessions(Long userId) {
        participantRepository.detachUserFromEndedSessions(userId, ACTIVE_STATUSES);
    }
}
