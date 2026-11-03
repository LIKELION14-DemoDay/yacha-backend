package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
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
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;
import likelion.yacha_backend.global.util.DateTimes;
import likelion.yacha_backend.domain.topic.entity.Category;
import java.util.List;
import likelion.yacha_backend.domain.topic.service.TopicService;

/**
 * 랜덤 방 생성 · 입장 · 대기 취소 · AI 전환의 DB 작업 (명세 2-3-4). 게임 생성 · 알림처럼 커밋 뒤에 할 일은
 * {@link SessionMatchFacade} 가 맡습니다.
 *
 * <p><b>같은 사용자의 요청이 겹칠 때</b> — 생성 · 입장은 먼저 사용자 행을 잠급니다({@code SELECT ... FOR UPDATE}).
 * "참여 중인 세션 확인 → 저장" 사이에 같은 사용자의 다른 요청이 끼어들면 방이 두 개 생기거나 게임 두 개에 들어가기 때문입니다.
 * 잠금은 커밋까지 유지되므로 뒤 요청은 앞 요청이 저장한 세션을 보고 {@code ALREADY_IN_SESSION} 을 받습니다.
 * 이때 잠금은 반드시 트랜잭션의 첫 쿼리여야 합니다. MySQL REPEATABLE READ 에서는 첫 SELECT 때 스냅샷이 잡히므로,
 * 잠금 앞에 일반 조회가 있으면 앞 요청이 저장한 세션을 보지 못합니다.
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
    private final TopicService topicService;
    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final WaitTimerProperties waitTimerProperties;
    private final Clock clock;
    // 초대 코드 생성에 사용할 문자
    private static final String INVITE_CODE_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int INVITE_CODE_LENGTH = 10;

    // 예측하기 어려운 코드를 생성하기 위한 난수 생성기
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 친구 방 생성 결과 */
    public record FriendCreated(
            Long sessionId,
            String inviteCode,
            LocalDateTime createdAt,
            OffsetDateTime expiresAt
    ) {
    }

    /** 만든 대기 방. 커밋 뒤 대기 타이머를 등록하는 데 씁니다 ({@code createdAt} 이 타이머 기준). */
    public record Created(Long sessionId, LocalDateTime createdAt) {
    }

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
    public Created create(Long userId, SessionCreateRequest request) {
        if (request.roomType() != RoomType.RANDOM) {
            // 친구 방은 createFriend 메서드에서 별도로 처리합니다.
            throw new BusinessException(GlobalErrorCode.VALIDATION_FAILED);
        }
        User user = lockUser(userId);
        checkNotInSession(userId);
        Topic topic = activeTopic(request.topicId());

        DebateSession session = sessionRepository.save(DebateSession.createRandom(topic));
        participantRepository.save(DebateParticipant.initiator(session, user, request.stance(), LocalDateTime.now(clock)));
        return new Created(session.getId(), session.getCreatedAt());
    }


    /**
     * 친구 초대 방 생성
     * 회원만 생성할 수 있으며 초대 코드를 발급한다.
     */
    public FriendCreated createFriend(Long userId, Category category) {

        // 1. 사용자 잠금 및 조회
        User user = lockUser(userId);

        // 2. 게스트는 친구 초대 방을 생성할 수 없음
        if (user.isGuest()) {
            throw new BusinessException(
                    GlobalErrorCode.GUEST_NOT_ALLOWED
            );
        }

        // 3. 이미 대기 중이거나 진행 중인 토론이 있는지 확인
        checkNotInSession(userId);

        // 선택한 카테고리에 활성 주제가 없으면 방장에게 즉시 알립니다.
        if (!topicRepository.existsByCategoryAndActiveTrue(category)) {
            throw new BusinessException(TopicErrorCode.TOPIC_NOT_FOUND);
        }

        // 4. 중복되지 않는 초대 코드 생성
        String inviteCode = generateUniqueInviteCode();

        // 5. 친구 방 생성 및 DB 저장
        DebateSession session = sessionRepository.save(
                DebateSession.createFriend(category, inviteCode)
        );

        // 6. 방장 참가자 등록
        // 친구가 들어온 뒤 찬반을 정하므로 현재는 null
        participantRepository.save(
                DebateParticipant.initiator(
                        session,
                        user,
                        null,
                        LocalDateTime.now(clock)
                )
        );

        // 7. 생성된 방 정보 반환
        return new FriendCreated(
                session.getId(),
                inviteCode,
                session.getCreatedAt(),
                DateTimes.withOffset(
                        session.getCreatedAt().plus(WaitTimerProperties.FRIEND_LIMIT),
                        clock.getZone()
                )
        );
    }

    /**
     * 고유 초대 코드 생성
     */
    private String generateUniqueInviteCode() {

        for (int attempt = 0; attempt < 5; attempt++) {

            StringBuilder code = new StringBuilder();

            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                int index = SECURE_RANDOM.nextInt(
                        INVITE_CODE_CHARS.length()
                );

                code.append(INVITE_CODE_CHARS.charAt(index));
            }

            String inviteCode = code.toString();

            // 기존 DB에 없는 코드인지 확인
            if (sessionRepository.findByInviteCode(inviteCode).isEmpty()) {
                return inviteCode;
            }
        }

        throw new BusinessException(
                GlobalErrorCode.INTERNAL_SERVER_ERROR
        );
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
     * 초대 코드를 이용한 친구 방 입장
     */
    public Matched joinFriend(Long userId, String inviteCode) {

        // 1. 사용자 잠금
        User user = lockUser(userId);

        // 2. 초대 코드로 친구 방 조회
        String normalizedCode = inviteCode.trim().toUpperCase(Locale.ROOT);
        DebateSession session = sessionRepository
                .findByInviteCode(normalizedCode)
                .orElseThrow(() -> new BusinessException(
                        SessionErrorCode.INVALID_INVITE_CODE
                ));

        Long sessionId = session.getId();

        // 3. 방장 정보 조회
        DebateParticipant host = hostOf(sessionId);

        // 본인이 만든 방에는 입장할 수 없음
        if (host.isUser(userId)) {
            throw new BusinessException(
                    SessionErrorCode.CANNOT_JOIN_OWN_ROOM
            );
        }

        // 4. 만료 여부 확인
        LocalDateTime now = startTime();

        if (!now.isBefore(session.getCreatedAt().plus(WaitTimerProperties.FRIEND_LIMIT))) {
            throw new BusinessException(
                    SessionErrorCode.INVITE_EXPIRED
            );
        }

        // 5. 친구 방이며 대기 중인지 확인
        if (session.getRoomType() != RoomType.FRIEND
                || !session.isWaiting()
                || session.getMode() != SessionMode.HUMAN
                || host.getUser() == null) {

            throw new BusinessException(
                    SessionErrorCode.SESSION_NOT_WAITING
            );
        }

        // 6. 이미 다른 토론에 참여 중인지 확인
        checkNotInSession(userId);

        // 7. 기존 주제 서비스로 랜덤 주제 선정
        Long topicId = topicService
                .pickRandom(session.getCategory(), List.of())
                .id();

        Topic topic = activeTopic(topicId);

        // 8. 방장 입장을 랜덤 배정
        Stance hostStance = SECURE_RANDOM.nextBoolean()
                ? Stance.AGREE
                : Stance.DISAGREE;

        Stance opponentStance = hostStance.opposite();

        // 9. 필요한 참가자 정보 보관
        Long hostUserId = host.getUser().getId();
        Long hostParticipantId = host.getId();

        // 10. 방장의 찬반 입장 배정
        host.assignStance(hostStance);

        // 11. WAITING -> IN_PROGRESS
        // 동시에 입장하더라도 한 요청만 성공
        if (sessionRepository.startFriendIfWaiting(
                sessionId, topic, now) == 0) {

            throw new BusinessException(
                    SessionErrorCode.SESSION_NOT_WAITING
            );
        }

        // 12. 친구를 상대 참가자로 등록
        DebateParticipant opponent =
                participantRepository.save(
                        DebateParticipant.opponent(
                                sessionRepository.getReferenceById(sessionId),
                                user,
                                opponentStance,
                                now
                        )
                );

        // 13. 기존 게임 생성에 필요한 정보 반환
        return new Matched(
                sessionId,
                now,
                hostUserId,
                Map.of(
                        hostUserId, hostParticipantId,
                        userId, opponent.getId()
                ),
                null
        );
    }

    /**
     * 방장이 기다리던 방을 봇전으로 바꿉니다. AI 는 방장의 반대 입장입니다.
     * 방을 만들고 첫 대기 알림({@code WAIT_PROMPT}, 30초)이 나가기 전이면 {@code BOT_NOT_AVAILABLE_YET} 입니다.
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
        if (session.getRoomType() != RoomType.RANDOM || !session.isWaiting()) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_WAITING);
        }
        // 봇전 버튼은 첫 대기 알림과 같이 뜹니다. 그 전에 부르면 버튼 없이 부른 것이라 막습니다.
        if (LocalDateTime.now(clock).isBefore(session.getCreatedAt().plus(waitTimerProperties.promptInterval()))) {
            throw new BusinessException(SessionErrorCode.BOT_NOT_AVAILABLE_YET);
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
