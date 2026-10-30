package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.dto.SessionFinishedEvent;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 주기 정리가 종료 알림을 보내는지 확인하려고 {@link TimerTestSupport} 의 모의 빈을 함께 씁니다. */
@DisplayName("InProgressSessionCleaner — 재시작 시 · 주기적으로 진행 중 게임 정리")
class InProgressSessionCleanerTest extends TimerTestSupport {

    private static final long IN_CHAT_1 = 90;
    /** 시간표(260초)와 정리 간격(1분)이 모두 지난 게임. */
    private static final long OVERDUE = 400;

    @Autowired
    private InProgressSessionCleaner cleaner;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private GameRegistry gameRegistry;

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
        Room bot = track(fixture.convertedBot(IN_CHAT_1));

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

    @Test
    @DisplayName("주기 정리 — 시간표가 지났는데 진행 중인 게임은 260초 종료처럼 끝내고, 반복돼도 알림은 한 번이다")
    void sweepFinishesOverdueGame() {
        Room room = track(fixture.randomHuman(OVERDUE));

        cleaner.sweepOverdue();
        cleaner.sweepOverdue();

        assertThat(session(room).getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session(room).getFinishReason()).isEqualTo(FinishReason.COMPLETED);
        assertThat(gameRegistry.find(room.sessionId()).orElseThrow().isFinished()).isTrue();
        verify(gameMessageService, times(1)).broadcastEvent(eq(room.sessionId()), argThat(event ->
                event instanceof SessionFinishedEvent finished && finished.reason() == FinishReason.COMPLETED));
    }

    @Test
    @DisplayName("주기 정리 — 게임이 메모리에 없으면(게임 생성 실패) ABORTED 로 끝낸다")
    void sweepAbortsWithoutGame() {
        Room room = fixture.randomHuman(OVERDUE);
        fixture.removeGame(room);

        cleaner.sweepOverdue();

        assertThat(session(room).getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session(room).getFinishReason()).isEqualTo(FinishReason.ABORTED);
        verify(gameMessageService, never()).broadcastEvent(eq(room.sessionId()), argThat(event ->
                event instanceof SessionFinishedEvent));
    }

    @Test
    @DisplayName("주기 정리 — 진행 중이거나 시간표가 끝난 지 정리 간격이 안 된 게임은 건드리지 않는다")
    void sweepLeavesRecentGames() {
        Room chatting = track(fixture.randomHuman(IN_CHAT_1));
        Room justEnded = track(fixture.randomHuman(300));

        cleaner.sweepOverdue();

        assertThat(session(chatting).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session(justEnded).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("주기 정리 — 종료에 실패한 게임은 다음 주기에 다시 끝낸다")
    void sweepRetriesFailedFinish() {
        Room room = track(fixture.randomHuman(OVERDUE));
        // 첫 정리에서만 종료가 실패한다 (DB 가 잠깐 끊긴 경우)
        doThrow(new IllegalStateException("DB 끊김")).doCallRealMethod()
                .when(phaseScheduler).finish(room.sessionId());

        cleaner.sweepOverdue();
        assertThat(session(room).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);

        cleaner.sweepOverdue();
        assertThat(session(room).getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session(room).getFinishReason()).isEqualTo(FinishReason.COMPLETED);
    }
}
