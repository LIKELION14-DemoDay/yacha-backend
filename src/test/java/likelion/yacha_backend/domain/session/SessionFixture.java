package likelion.yacha_backend.domain.session;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 토론방 테스트 데이터. 방 종류별로 세션 · 참가자를 만들고, 진행 중인 방은 게임 메모리도 만듭니다.
 *
 * <p>{@code @Import(SessionFixture.class)} 로 씁니다. 시작한 지 몇 초 지난 방처럼 매칭 API 로는 만들 수 없는 상태가
 * 필요해서, 매칭 성사 처리(시작 UPDATE → 게임 생성)를 여기서 흉내 냅니다. 게임은 테스트가 끝날 때 {@link #removeGame} 으로 지웁니다.
 */
@TestComponent
public class SessionFixture {

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final GameRegistry gameRegistry;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SessionFixture(UserRepository userRepository, TopicRepository topicRepository,
                          DebateSessionRepository sessionRepository,
                          DebateParticipantRepository participantRepository, GameRegistry gameRegistry,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.gameRegistry = gameRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 만든 방. 사람 참가자의 userId · participantId 를 담습니다.
     *
     * @param opponentUserId 봇전이면 null (상대가 AI)
     */
    public record Room(Long sessionId, Long hostUserId, Long hostParticipantId,
                       Long opponentUserId, Long opponentParticipantId) {
    }

    /** 윤리 카테고리의 주제. 방마다 새로 만듭니다. */
    public Topic topic() {
        return topicRepository.save(Topic.create(Subcategory.GOOD_AND_EVIL, "거짓말은 언제나 나쁜가?",
                "언제나 나쁘다", "그렇지 않다"));
    }

    public Long newUser() {
        return userRepository.save(User.createGuest("u" + UUID.randomUUID().toString().substring(0, 8))).getId();
    }

    /** 진행 중인 랜덤 사람전. 관전할 수 있는 유일한 방입니다. {@code elapsedSeconds} 는 시작 후 흐른 시간. */
    public Room randomHuman(long elapsedSeconds) {
        LocalDateTime startedAt = startedAt(elapsedSeconds);
        Room room = inTransaction(() -> {
            DebateSession session = sessionRepository.save(DebateSession.createRandom(topic()));
            User host = user();
            User opponent = user();
            Long hostParticipant = participantRepository.save(
                    DebateParticipant.initiator(session, host, Stance.AGREE, startedAt)).getId();
            Long opponentParticipant = participantRepository.save(
                    DebateParticipant.opponent(session, opponent, Stance.DISAGREE, startedAt)).getId();
            sessionRepository.startIfWaiting(session.getId(), startedAt);
            return new Room(session.getId(), host.getId(), hostParticipant, opponent.getId(), opponentParticipant);
        });
        createGame(room, startedAt);
        return room;
    }

    /** 진행 중인 친구 방. 관전 없이 항상 비공개입니다. */
    public Room friend(long elapsedSeconds) {
        LocalDateTime startedAt = startedAt(elapsedSeconds);
        Room room = inTransaction(() -> {
            DebateSession session = sessionRepository.save(
                    DebateSession.createFriend(Category.ETHICS, UUID.randomUUID().toString().substring(0, 8)));
            User host = user();
            User friend = user();
            Long hostParticipant = participantRepository.save(
                    DebateParticipant.initiator(session, host, Stance.DISAGREE, startedAt)).getId();
            Long friendParticipant = participantRepository.save(
                    DebateParticipant.opponent(session, friend, Stance.AGREE, startedAt)).getId();
            sessionRepository.startFriendIfWaiting(session.getId(), topic(), startedAt);
            return new Room(session.getId(), host.getId(), hostParticipant, friend.getId(), friendParticipant);
        });
        createGame(room, startedAt);
        return room;
    }

    /** 대기 중에 "AI와 대결" 로 바뀐 봇전. {@code room_type} 은 RANDOM 그대로라 {@code mode} 로만 구분됩니다. */
    public Room convertedBot(long elapsedSeconds) {
        LocalDateTime startedAt = startedAt(elapsedSeconds);
        Room room = inTransaction(() -> {
            DebateSession session = sessionRepository.save(DebateSession.createRandom(topic()));
            User host = user();
            Long hostParticipant = participantRepository.save(
                    DebateParticipant.initiator(session, host, Stance.AGREE, startedAt)).getId();
            participantRepository.save(DebateParticipant.ai(session, Stance.DISAGREE, startedAt));
            sessionRepository.convertToAiIfWaiting(session.getId(), startedAt);
            return new Room(session.getId(), host.getId(), hostParticipant, null, null);
        });
        createGame(room, startedAt);
        return room;
    }

    /** 제안을 모두 거절해 서버가 만든 자동 봇전. 이것도 {@code room_type} 이 RANDOM 입니다. */
    public Room autoBot(long elapsedSeconds) {
        LocalDateTime startedAt = startedAt(elapsedSeconds);
        Room room = inTransaction(() -> {
            DebateSession session = sessionRepository.save(
                    DebateSession.createAiMatch(topic(), startedAt));
            User host = user();
            Long hostParticipant = participantRepository.save(
                    DebateParticipant.initiator(session, host, Stance.AGREE, startedAt)).getId();
            participantRepository.save(DebateParticipant.ai(session, Stance.DISAGREE, startedAt));
            return new Room(session.getId(), host.getId(), hostParticipant, null, null);
        });
        createGame(room, startedAt);
        return room;
    }

    /** 상대를 기다리는 랜덤 방. 게임이 아직 없습니다. */
    public Room waitingRandom() {
        return inTransaction(() -> {
            DebateSession session = sessionRepository.save(DebateSession.createRandom(topic()));
            User host = user();
            Long hostParticipant = participantRepository.save(
                    DebateParticipant.initiator(session, host, Stance.AGREE, LocalDateTime.now(clock))).getId();
            return new Room(session.getId(), host.getId(), hostParticipant, null, null);
        });
    }

    /**
     * 게임 종료 처리를 흉내 냅니다. 세션을 {@code FINISHED} 로 바꾸고 게임은 막기만 합니다.
     * 정상 종료는 판정이 끝날 때까지 메시지가 메모리에 남아 있습니다.
     */
    public void finish(Room room) {
        inTransaction(() -> sessionRepository.finishIfInProgress(
                room.sessionId(), FinishReason.COMPLETED, LocalDateTime.now(clock)));
        gameRegistry.find(room.sessionId()).orElseThrow().finish();
    }

    public void removeGame(Room room) {
        if (room != null) {
            gameRegistry.remove(room.sessionId());
        }
    }

    private void createGame(Room room, LocalDateTime startedAt) {
        Map<Long, Long> participantIdByUserId = new HashMap<>();
        participantIdByUserId.put(room.hostUserId(), room.hostParticipantId());
        if (room.opponentUserId() != null) {
            participantIdByUserId.put(room.opponentUserId(), room.opponentParticipantId());
        }
        gameRegistry.create(room.sessionId(), startedAt, participantIdByUserId);
    }

    private LocalDateTime startedAt(long elapsedSeconds) {
        return LocalDateTime.now(clock).minusSeconds(elapsedSeconds);
    }

    private User user() {
        return userRepository.findById(newUser()).orElseThrow();
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
