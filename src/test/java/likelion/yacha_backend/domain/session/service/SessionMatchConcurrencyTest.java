package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.dto.BotMatchRequest;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.entity.ParticipantType;
import likelion.yacha_backend.domain.session.entity.SessionMode;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 요청이 동시에 들어올 때 — 실제로 커밋해야 잠금 · 조건부 UPDATE 가 동작하므로 테스트 트랜잭션을 쓰지 않습니다.
 */
@SpringBootTest
@Import(SessionFixture.class)
@DisplayName("방 생성 · 입장 · 봇전 — 동시 요청")
class SessionMatchConcurrencyTest {

    @Autowired
    private SessionMatchFacade sessionMatchFacade;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private GameRegistry gameRegistry;

    private final List<Long> sessionIds = new ArrayList<>();

    @AfterEach
    void removeGames() {
        sessionIds.forEach(gameRegistry::remove);
    }

    @Test
    @DisplayName("두 사람이 같은 방에 동시에 들어오면 한 명만 들어가고, 늦은 쪽은 SESSION_NOT_WAITING")
    void onlyOneJoinsSameRoom() throws Exception {
        Room room = fixture.waitingRandom();
        sessionIds.add(room.sessionId());
        Long first = fixture.newUser();
        Long second = fixture.newUser();

        List<Outcome> outcomes = runTogether(
                () -> sessionMatchFacade.join(first, room.sessionId()),
                () -> sessionMatchFacade.join(second, room.sessionId()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::errorCode).containsExactly("SESSION_NOT_WAITING");
        assertThat(participantRepository.findAllBySession_Id(room.sessionId())).hasSize(2);
        assertThat(gameRegistry.find(room.sessionId())).isPresent();
    }

    @Test
    @DisplayName("같은 사용자가 방을 동시에 두 번 만들면 하나만 만들어지고, 다른 쪽은 ALREADY_IN_SESSION")
    void sameUserCreatesOnce() throws Exception {
        Long userId = fixture.newUser();
        SessionCreateRequest request = new SessionCreateRequest(RoomType.RANDOM, fixture.topic().getId(), Stance.AGREE);

        List<Outcome> outcomes = runTogether(
                () -> sessionMatchFacade.create(userId, request),
                () -> sessionMatchFacade.create(userId, request));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::errorCode).containsExactly("ALREADY_IN_SESSION");
        Long sessionId = outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow().sessionId();
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getStatus()).isEqualTo(SessionStatus.WAITING);
    }

    @Test
    @DisplayName("같은 사용자가 두 방에 동시에 들어가면 한 방에만 들어가고, 다른 쪽은 ALREADY_IN_SESSION")
    void sameUserJoinsOneRoom() throws Exception {
        Room roomA = fixture.waitingRandom();
        Room roomB = fixture.waitingRandom();
        sessionIds.add(roomA.sessionId());
        sessionIds.add(roomB.sessionId());
        Long userId = fixture.newUser();

        List<Outcome> outcomes = runTogether(
                () -> sessionMatchFacade.join(userId, roomA.sessionId()),
                () -> sessionMatchFacade.join(userId, roomB.sessionId()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::errorCode).containsExactly("ALREADY_IN_SESSION");
        long started = List.of(roomA, roomB).stream()
                .filter(room -> sessionRepository.findById(room.sessionId()).orElseThrow().isInProgress())
                .count();
        assertThat(started).isEqualTo(1);
    }

    @Test
    @DisplayName("AI 전환과 입장이 동시에 오면 한쪽만 성공하고, 늦은 쪽은 SESSION_NOT_WAITING")
    void convertRacesWithJoin() throws Exception {
        Room room = fixture.waitingRandom();
        sessionIds.add(room.sessionId());
        Long joiner = fixture.newUser();

        List<Outcome> outcomes = runTogether(
                () -> sessionMatchFacade.convertToAi(room.hostUserId(), room.sessionId()),
                () -> sessionMatchFacade.join(joiner, room.sessionId()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::errorCode).containsExactly("SESSION_NOT_WAITING");
        // 이긴 쪽에 맞게 참가자가 2명(방장 + AI 또는 방장 + 입장한 사람)이고 모드도 맞아야 합니다.
        var participants = participantRepository.findAllBySession_Id(room.sessionId());
        assertThat(participants).hasSize(2);
        boolean aiWon = participants.stream().anyMatch(p -> p.getParticipantType() == ParticipantType.AI);
        assertThat(sessionRepository.findById(room.sessionId()).orElseThrow().getMode())
                .isEqualTo(aiWon ? SessionMode.AI : SessionMode.HUMAN);
        assertThat(gameRegistry.find(room.sessionId())).isPresent();
    }

    @Test
    @DisplayName("같은 사용자가 봇전을 동시에 두 번 시작하면 하나만 만들어지고, 다른 쪽은 ALREADY_IN_SESSION")
    void sameUserStartsBotOnce() throws Exception {
        Long userId = fixture.newUser();
        BotMatchRequest request = new BotMatchRequest(fixture.topic().getId(), Stance.AGREE);

        List<Outcome> outcomes = runTogether(
                () -> sessionMatchFacade.startBot(userId, request),
                () -> sessionMatchFacade.startBot(userId, request));
        outcomes.stream().filter(Outcome::succeeded).forEach(outcome -> sessionIds.add(outcome.sessionId()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::errorCode).containsExactly("ALREADY_IN_SESSION");
    }

    /** 성공하면 세션 id, 실패하면 에러 코드. */
    private record Outcome(Long sessionId, String errorCode) {

        boolean succeeded() {
            return sessionId != null;
        }
    }

    /** 두 작업을 같은 순간에 시작시키고 결과를 모읍니다. 비즈니스 예외가 아닌 예외는 그대로 테스트 실패입니다. */
    private List<Outcome> runTogether(Callable<Long> first, Callable<Long> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Long> task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome(task.call(), null);
                    } catch (BusinessException e) {
                        return new Outcome(null, e.getErrorCode().name());
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
