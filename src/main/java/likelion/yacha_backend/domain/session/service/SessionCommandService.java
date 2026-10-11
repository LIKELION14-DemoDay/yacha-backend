package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.session.dto.BotMatchRequest;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.ParticipantRole;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.SessionMode;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.exception.TopicErrorCode;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랜덤 방 생성 · 입장 · 대기 취소 · 봇전 시작의 DB 작업 (명세 2-3-4). 게임 생성 · 알림처럼 커밋 뒤에 할 일은
 * {@link SessionMatchFacade} 가 맡습니다.
 *
 * <p><b>같은 사용자의 요청이 겹칠 때</b> — 생성 · 입장은 먼저 사용자 행을 잠급니다({@code SELECT ... FOR UPDATE}).
 * "참여 중인 세션 확인 → 저장" 사이에 같은 사용자의 다른 요청이 끼어들면 방이 두 개 생기거나 게임 두 개에 들어가기 때문입니다.
 * 잠금은 커밋까지 유지되므로 뒤 요청은 앞 요청이 저장한 세션을 보고 {@code ALREADY_IN_SESSION} 을 받습니다.
 *
 * <p><b>다른 사용자끼리 겹칠 때</b> — 같은 방에 두 사람이 들어오거나, 입장과 취소 · AI 전환이 겹치면
 * {@code status = WAITING} 조건부 UPDATE 가 한쪽만 성공시킵니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SessionCommandService {

    /** 이 상태의 세션에 참가 중이면 새 방을 만들거나 들어갈 수 없습니다. */
    private static final Set<SessionStatus> ACTIVE_STATUSES = EnumSet.of(SessionStatus.WAITING, SessionStatus.IN_PROGRESS);

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final Clock clock;

    /**
     * 매칭이 성사된 방. 커밋 뒤 게임을 만들고 방장에게 알리는 데 씁니다.
     *
     * @param participantIdByUserId 사람 참가자의 userId → participantId. 봇전이면 사용자 한 명
     * @param aiParticipantId       봇전의 AI 참가자 id. 사람전이면 null
     */
    public record Matched(Long sessionId, LocalDateTime startedAt, Long hostUserId,
                          Map<Long, Long> participantIdByUserId, Long aiParticipantId) {
    }

    /** 랜덤 방을 만들고 방장으로 들어갑니다. 상대를 기다리는 {@code WAITING} 이 됩니다. */
    public Long create(Long userId, SessionCreateRequest request) {
        if (request.roomType() != RoomType.RANDOM) {
            // 친구 방은 구현 보류 (명세 2-3-3)
            throw new BusinessException(GlobalErrorCode.VALIDATION_FAILED);
        }
        User user = lockUser(userId);
        checkNotInSession(userId);
        Topic topic = activeTopic(request.topicId());

        DebateSession session = sessionRepository.save(DebateSession.createRandom(topic));
        participantRepository.save(DebateParticipant.initiator(session, user, request.stance(), LocalDateTime.now(clock)));
        return session.getId();
    }

    /**
     * 대기 중인 랜덤 방에 방장의 반대 입장으로 들어가 바로 시작합니다.
     * 동시에 들어오면 먼저 성공한 사람만 들어가고, 늦은 사람은 {@code SESSION_NOT_WAITING} 입니다.
     */
    public Matched join(Long userId, Long sessionId) {
        User user = lockUser(userId);
        DebateSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(SessionErrorCode.SESSION_NOT_FOUND));
        DebateParticipant host = hostOf(sessionId);
        if (host.isUser(userId)) {
            throw new BusinessException(SessionErrorCode.CANNOT_JOIN_OWN_ROOM);
        }
        // 봇전은 처음부터 IN_PROGRESS 라 여기서 걸리고, 친구 방은 초대 코드로만 들어옵니다.
        // 방장 계정이 정리된(게스트 정리) 방도 들어갈 수 없습니다.
        if (!session.isWaiting() || session.getRoomType() != RoomType.RANDOM || session.getMode() != SessionMode.HUMAN
                || host.getUser() == null) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_WAITING);
        }
        checkNotInSession(userId);

        // UPDATE 가 영속성 컨텍스트를 비우므로 필요한 값은 미리 꺼내 둡니다 (DebateSessionRepository 주의 참고).
        Long hostUserId = host.getUser().getId();
        Long hostParticipantId = host.getId();
        LocalDateTime now = startTime();
        if (sessionRepository.startIfWaiting(sessionId, now) == 0) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_WAITING);
        }
        DebateParticipant opponent = participantRepository.save(DebateParticipant.opponent(
                sessionRepository.getReferenceById(sessionId), user, host.getStance().opposite(), now));

        return new Matched(sessionId, now, hostUserId,
                Map.of(hostUserId, hostParticipantId, userId, opponent.getId()), null);
    }

    /**
     * 바로 봇전. 대기 방 없이 사용자 참가자와 AI 참가자(반대 입장)를 함께 만들고 바로 시작합니다.
     * 랜덤 방 생성과 같이 사용자 행을 잠그고 {@code ALREADY_IN_SESSION} 을 검사합니다.
     */
    public Matched startBot(Long userId, BotMatchRequest request) {
        User user = lockUser(userId);
        checkNotInSession(userId);
        Topic topic = activeTopic(request.topicId());

        LocalDateTime now = startTime();
        DebateSession session = sessionRepository.save(DebateSession.createAiMatch(topic, now));
        DebateParticipant participant = participantRepository.save(
                DebateParticipant.initiator(session, user, request.stance(), now));
        DebateParticipant ai = participantRepository.save(
                DebateParticipant.ai(session, request.stance().opposite(), now));
        return new Matched(session.getId(), now, userId, Map.of(userId, participant.getId()), ai.getId());
    }

    /**
     * 방장이 기다리던 방을 봇전으로 바꿉니다. AI 는 방장의 반대 입장입니다.
     * 그 순간 사람이 들어와도 조건부 UPDATE 로 한쪽만 성공하고, 늦은 쪽은 {@code SESSION_NOT_WAITING} 입니다.
     */
    public Matched convertToAi(Long userId, Long sessionId) {
        DebateSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(SessionErrorCode.SESSION_NOT_FOUND));
        DebateParticipant host = hostOf(sessionId);
        if (!host.isUser(userId)) {
            throw new BusinessException(SessionErrorCode.NOT_ROOM_OWNER);
        }
        // 친구 방은 대기 팝업이 없어 봇전으로 바꾸지 않습니다.
        if (session.getRoomType() != RoomType.RANDOM) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_WAITING);
        }

        // UPDATE 가 영속성 컨텍스트를 비우므로 필요한 값은 미리 꺼내 둡니다.
        Long hostParticipantId = host.getId();
        Stance aiStance = host.getStance().opposite();
        LocalDateTime now = startTime();
        if (sessionRepository.convertToAiIfWaiting(sessionId, now) == 0) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_WAITING);
        }
        DebateParticipant ai = participantRepository.save(
                DebateParticipant.ai(sessionRepository.getReferenceById(sessionId), aiStance, now));
        return new Matched(sessionId, now, userId, Map.of(userId, hostParticipantId), ai.getId());
    }

    /** 방장이 대기를 취소합니다. 이미 누가 들어왔거나 취소된 방이면 {@code SESSION_NOT_WAITING} 입니다. */
    public void cancel(Long userId, Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_FOUND);
        }
        if (!hostOf(sessionId).isUser(userId)) {
            throw new BusinessException(SessionErrorCode.NOT_ROOM_OWNER);
        }
        if (sessionRepository.cancelIfWaiting(sessionId, LocalDateTime.now(clock)) == 0) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_WAITING);
        }
    }

    /**
     * 게임 시작 시각. 게임의 시작 시각과 DB 의 started_at 이 같아야 구간 계산이 어긋나지 않습니다.
     * DB 는 마이크로초(timestamp(6))까지만 저장하므로 같은 정밀도로 맞춥니다 (Linux 는 나노초까지 나옴).
     */
    private LocalDateTime startTime() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private Topic activeTopic(Long topicId) {
        return topicRepository.findByIdAndActiveTrue(topicId)
                .orElseThrow(() -> new BusinessException(TopicErrorCode.TOPIC_NOT_FOUND));
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
    }

    private void checkNotInSession(Long userId) {
        if (participantRepository.existsByUser_IdAndSession_StatusIn(userId, ACTIVE_STATUSES)) {
            throw new BusinessException(SessionErrorCode.ALREADY_IN_SESSION);
        }
    }

    private DebateParticipant hostOf(Long sessionId) {
        return participantRepository.findBySession_IdAndRole(sessionId, ParticipantRole.INITIATOR)
                .orElseThrow(() -> new BusinessException(SessionErrorCode.SESSION_NOT_FOUND));
    }
}
