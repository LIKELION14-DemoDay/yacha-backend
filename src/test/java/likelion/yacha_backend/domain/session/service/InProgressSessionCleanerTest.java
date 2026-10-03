package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(SessionFixture.class)
@DisplayName("InProgressSessionCleaner — 재시작 시 진행 중 게임 정리")
class InProgressSessionCleanerTest {

    private static final long IN_CHAT_1 = 90;

    @Autowired
    private InProgressSessionCleaner cleaner;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    private final List<Room> rooms = new ArrayList<>();

    @AfterEach
    void removeGames() {
        rooms.forEach(fixture::removeGame);
    }

    private Room track(Room room) {
        rooms.add(room);
        return room;
    }

    private DebateSession session(Room room) {
        return sessionRepository.findById(room.sessionId()).orElseThrow();
    }

    /** 지금 서버가 뜬 것으로 보고 정리. 픽스처 게임은 모두 이보다 먼저 시작됨 */
    private int abortAll() {
        return cleaner.abortInProgressSessions(LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("진행 중인 사람전 · 봇전을 FINISHED + ABORTED 로 끝내고 승패는 NULL 로 둔다")
    void abortsInProgress() {
        Room human = track(fixture.randomHuman(IN_CHAT_1));
        Room bot = track(fixture.autoBot(IN_CHAT_1));

        int aborted = abortAll();

        assertThat(aborted).isGreaterThanOrEqualTo(2);
        for (Room room : List.of(human, bot)) {
            DebateSession session = session(room);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
            assertThat(session.getFinishReason()).isEqualTo(FinishReason.ABORTED);
            assertThat(session.getEndedAt()).isNotNull();
            assertThat(participantRepository.findAllBySession_Id(room.sessionId()))
                    .extracting(DebateParticipant::getResult)
                    .containsOnlyNulls();
        }
        assertThat(sessionRepository.findAllByStatusAndStartedAtBefore(
                SessionStatus.IN_PROGRESS, LocalDateTime.now(clock))).isEmpty();
    }

    @Test
    @DisplayName("서버가 뜬 뒤 시작된 게임은 이 서버의 게임이라 끝내지 않는다")
    void keepsGamesStartedAfterBoot() {
        Room started = track(fixture.randomHuman(IN_CHAT_1));
        LocalDateTime bootTime = LocalDateTime.now(clock).minusSeconds(IN_CHAT_1 + 1);

        cleaner.abortInProgressSessions(bootTime);

        assertThat(session(started).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session(started).getFinishReason()).isNull();
    }

    @Test
    @DisplayName("대기 중 · 이미 끝난 · 취소된 방은 건드리지 않는다")
    void leavesOtherStatuses() {
        Room waiting = fixture.waitingRandom();
        Room finished = track(fixture.randomHuman(IN_CHAT_1));
        fixture.finish(finished);
        Room cancelled = fixture.waitingRandom();
        new TransactionTemplate(transactionManager).execute(
                status -> sessionRepository.cancelIfWaiting(cancelled.sessionId(), LocalDateTime.now(clock)));

        abortAll();

        assertThat(session(waiting).getStatus()).isEqualTo(SessionStatus.WAITING);
        assertThat(session(finished).getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session(finished).getFinishReason()).isEqualTo(FinishReason.COMPLETED);
        assertThat(session(cancelled).getStatus()).isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    @DisplayName("정리할 게임이 없으면 0 이고, 두 번 불러도 다시 끝내지 않는다")
    void idempotent() {
        abortAll();

        assertThat(abortAll()).isZero();
    }
}
