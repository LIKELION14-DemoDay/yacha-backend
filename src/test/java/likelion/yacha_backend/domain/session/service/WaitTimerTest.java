package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 대기 타이머 — 실제 타이머로 확인하므로 간격을 1초 · 상한을 3초 · 정리 간격을 1초로 줄입니다.
 * 타이머 작업은 커밋된 데이터를 읽으므로 테스트 트랜잭션을 쓰지 않습니다.
 */
@SpringBootTest
@Import(SessionFixture.class)
@TestPropertySource(properties = {"game.wait.prompt-interval=1s", "game.wait.limit=3s", "game.wait.sweep-interval=1s"})
@DisplayName("대기 타이머 — WAIT_PROMPT · 5분 상한 · 재시작 복구 · 주기 정리")
class WaitTimerTest {

    /** 상한(3초) 뒤 만료 작업까지 끝나기를 기다리는 시간. */
    private static final long AFTER_LIMIT_MILLIS = 4_000;

    @MockitoBean
    private MatchNotifier matchNotifier;

    @Autowired
    private SessionMatchFacade sessionMatchFacade;

    @Autowired
    private WaitingSessionRecovery waitingSessionRecovery;

    @MockitoSpyBean
    private WaitTimerService waitTimerService;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private final List<Long> sessionIds = new ArrayList<>();

    @AfterEach
    void removeGames() {
        sessionIds.forEach(gameRegistry::remove);
    }

    private Long createRoom(Long userId) {
        Long sessionId = sessionMatchFacade.create(userId,
                new SessionCreateRequest(RoomType.RANDOM, fixture.topic().getId(), Stance.AGREE));
        sessionIds.add(sessionId);
        return sessionId;
    }

    private DebateSession session(Long sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow();
    }

    @Test
    @DisplayName("기다리는 동안 간격마다 방장에게 WAIT_PROMPT 를 보내고, 상한이 되면 방을 취소하고 WAIT_EXPIRED 를 보낸다")
    void promptsThenExpires() {
        Long hostId = fixture.newUser();
        Long sessionId = createRoom(hostId);

        ArgumentCaptor<LocalDateTime> expiresAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(matchNotifier, timeout(AFTER_LIMIT_MILLIS)).waitPrompt(eq(hostId), eq(sessionId), eq(1L), expiresAt.capture());
        verify(matchNotifier, timeout(AFTER_LIMIT_MILLIS)).waitPrompt(eq(hostId), eq(sessionId), eq(2L), any());
        verify(matchNotifier, timeout(AFTER_LIMIT_MILLIS)).waitExpired(hostId, sessionId);

        DebateSession session = session(sessionId);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(expiresAt.getValue()).isCloseTo(session.getCreatedAt().plusSeconds(3), within(1, ChronoUnit.MILLIS));
        // 상한 시각에는 팝업 대신 취소만 합니다.
        verify(matchNotifier, never()).waitPrompt(anyLong(), eq(sessionId), eq(3L), any());
        // 취소됐으므로 새 방을 만들 수 있습니다.
        createRoom(hostId);
    }

    @Test
    @DisplayName("상대가 들어오면 남은 팝업 · 취소가 없다")
    void joinStopsTimers() {
        Long hostId = fixture.newUser();
        Long sessionId = createRoom(hostId);

        sessionMatchFacade.join(fixture.newUser(), sessionId);

        verify(matchNotifier, after(AFTER_LIMIT_MILLIS).never()).waitPrompt(anyLong(), eq(sessionId), anyLong(), any());
        verify(matchNotifier, never()).waitExpired(anyLong(), eq(sessionId));
        assertThat(session(sessionId).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("방장이 취소하면 남은 팝업 · 취소 알림이 없다")
    void cancelStopsTimers() {
        Long hostId = fixture.newUser();
        Long sessionId = createRoom(hostId);

        sessionMatchFacade.cancel(hostId, sessionId);

        verify(matchNotifier, after(AFTER_LIMIT_MILLIS).never()).waitPrompt(anyLong(), eq(sessionId), anyLong(), any());
        verify(matchNotifier, never()).waitExpired(anyLong(), eq(sessionId));
    }

    @Test
    @DisplayName("AI 전환하면 남은 팝업 · 취소가 없고 봇전이 계속된다")
    void convertStopsTimers() {
        Long hostId = fixture.newUser();
        Long sessionId = createRoom(hostId);

        sessionMatchFacade.convertToAi(hostId, sessionId);

        verify(matchNotifier, after(AFTER_LIMIT_MILLIS).never()).waitPrompt(anyLong(), eq(sessionId), anyLong(), any());
        verify(matchNotifier, never()).waitExpired(anyLong(), eq(sessionId));
        assertThat(session(sessionId).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("팝업을 확인하고 보내는 동안 들어온 입장은 기다렸다가 시작하고, 방장은 WAIT_PROMPT 다음에 MATCHED 를 받는다")
    void joinWaitsForPromptInFlight() throws Exception {
        Long hostId = fixture.newUser();
        Long guestId = fixture.newUser();
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(matchNotifier).waitPrompt(eq(hostId), anyLong(), eq(1L), any());
        Long sessionId = createRoom(hostId);

        // 1초 팝업이 WAITING 을 확인하고 보내는 중
        assertThat(sending.await(AFTER_LIMIT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        CompletableFuture<Long> join = CompletableFuture.supplyAsync(() -> sessionMatchFacade.join(guestId, sessionId));

        // 팝업이 행을 잠그고 있으므로 입장은 시작하지 못하고 기다린다
        Thread.sleep(300);
        assertThat(join).isNotDone();
        assertThat(session(sessionId).getStatus()).isEqualTo(SessionStatus.WAITING);

        release.countDown();
        join.get(5, TimeUnit.SECONDS);

        InOrder order = inOrder(matchNotifier);
        order.verify(matchNotifier).waitPrompt(eq(hostId), eq(sessionId), eq(1L), any());
        order.verify(matchNotifier).matched(hostId, sessionId);
        verify(matchNotifier, after(AFTER_LIMIT_MILLIS).never()).waitPrompt(anyLong(), eq(sessionId), eq(2L), any());
        assertThat(session(sessionId).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("타이머가 늦게 돌아 상한이 지났거나 이미 시작된 방이면 팝업을 보내지 않는다")
    void skipsStalePrompt() {
        Room waiting = fixture.waitingRandom();
        Room started = fixture.waitingRandom();
        sessionIds.add(started.sessionId());
        sessionMatchFacade.join(fixture.newUser(), started.sessionId());
        LocalDateTime now = LocalDateTime.now(clock);

        waitTimerService.prompt(waiting.sessionId(), waiting.hostUserId(), 30, now.minusSeconds(1));
        waitTimerService.prompt(started.sessionId(), started.hostUserId(), 30, now.plusMinutes(5));

        verify(matchNotifier, never()).waitPrompt(anyLong(), eq(waiting.sessionId()), eq(30L), any());
        verify(matchNotifier, never()).waitPrompt(anyLong(), eq(started.sessionId()), eq(30L), any());
    }

    @Test
    @DisplayName("재시작 복구 — 상한이 지난 방은 바로 취소, 남은 방은 다시 등록, 기준 시각 뒤에 만든 방은 건너뛴다")
    void restoresWaitingRooms() {
        Room overdue = fixture.waitingRandom();
        Room recent = fixture.waitingRandom();
        LocalDateTime now = LocalDateTime.now(clock);
        setCreatedAt(overdue.sessionId(), now.minusSeconds(10));
        setCreatedAt(recent.sessionId(), now.minusSeconds(1));
        LocalDateTime bootTime = LocalDateTime.now(clock);
        Room afterBoot = fixture.waitingRandom();
        setCreatedAt(afterBoot.sessionId(), bootTime.plusSeconds(1));

        int restored = waitingSessionRecovery.restore(bootTime);

        assertThat(restored).isGreaterThanOrEqualTo(2);
        verify(matchNotifier, timeout(1_000)).waitExpired(overdue.hostUserId(), overdue.sessionId());
        // 지난 팝업(1초)은 건너뛰고 2초 팝업부터, 상한에 취소
        verify(matchNotifier, timeout(AFTER_LIMIT_MILLIS)).waitPrompt(eq(recent.hostUserId()), eq(recent.sessionId()), eq(2L), any());
        verify(matchNotifier, timeout(AFTER_LIMIT_MILLIS)).waitExpired(recent.hostUserId(), recent.sessionId());
        verify(matchNotifier, never()).waitPrompt(anyLong(), eq(recent.sessionId()), eq(1L), any());
        verify(matchNotifier, after(500).never()).waitExpired(anyLong(), eq(afterBoot.sessionId()));
        assertThat(session(overdue.sessionId()).getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(session(afterBoot.sessionId()).getStatus()).isEqualTo(SessionStatus.WAITING);
    }

    @Test
    @DisplayName("타이머 등록이 실패해도 방 생성은 성공하고, 상한이 지난 뒤 주기 정리가 방을 취소한다")
    void sweepCancelsRoomWithoutTimer() {
        Long hostId = fixture.newUser();
        // 방을 만들 때의 등록만 실패시키고, 주기 정리의 재등록은 그대로 둔다
        doThrow(new IllegalStateException("등록 실패")).doCallRealMethod()
                .when(waitTimerService).register(anyLong(), eq(hostId), any());

        Long sessionId = createRoom(hostId);
        assertThat(session(sessionId).getStatus()).isEqualTo(SessionStatus.WAITING);
        // 타이머가 없으므로 상한(3초)이 지나도 취소되지 않는다
        verify(matchNotifier, after(AFTER_LIMIT_MILLIS).never()).waitExpired(anyLong(), eq(sessionId));
        // 상한 + 정리 간격(4초)을 확실히 넘기도록 만든 시각을 앞당긴다
        setCreatedAt(sessionId, LocalDateTime.now(clock).minusSeconds(10));
        Room fresh = fixture.waitingRandom();

        waitingSessionRecovery.sweepExpired();

        verify(matchNotifier, timeout(1_000)).waitExpired(hostId, sessionId);
        assertThat(session(sessionId).getStatus()).isEqualTo(SessionStatus.CANCELLED);
        // 상한 + 정리 간격이 지나지 않은 방은 건드리지 않는다
        verify(matchNotifier, after(500).never()).waitExpired(anyLong(), eq(fresh.sessionId()));
        assertThat(session(fresh.sessionId()).getStatus()).isEqualTo(SessionStatus.WAITING);
        // 취소됐으므로 방장은 새 방을 만들 수 있다
        createRoom(hostId);
    }

    private void setCreatedAt(Long sessionId, LocalDateTime createdAt) {
        jdbcTemplate.update("update debate_session set created_at = ? where id = ?", createdAt, sessionId);
    }
}
