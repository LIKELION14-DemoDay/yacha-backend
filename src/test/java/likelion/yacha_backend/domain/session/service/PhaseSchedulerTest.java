package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.dto.BotMatchRequest;
import likelion.yacha_backend.domain.session.dto.FinalNoticeEvent;
import likelion.yacha_backend.domain.session.dto.PhaseChangedEvent;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.dto.SessionFinishedEvent;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameMessage;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.MessageType;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 구간 스케줄러 — 실제 타이머로 확인합니다 ({@link TimerTestSupport}). 시작한 지 몇 초 지난 게임을 만들어
 * 다음 시각이 곧 오게 합니다.
 */
@DisplayName("구간 스케줄러 — 공개 · 구간 전환 · 최종반론 · 260초 종료")
class PhaseSchedulerTest extends TimerTestSupport {

    private static final long WAIT_MILLIS = 6_000;



    @Autowired
    private SessionFixture fixture;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private SessionMatchFacade sessionMatchFacade;

    private final List<Room> rooms = new ArrayList<>();
    private final List<Long> sessionIds = new ArrayList<>();

    @AfterEach
    void removeGames() {
        rooms.forEach(fixture::removeGame);
        sessionIds.forEach(gameRegistry::remove);
    }

    /** 시작한 지 {@code elapsedSeconds} 지난 사람전을 만들고 시간표를 등록합니다. */
    private Room startedRoom(long elapsedSeconds) {
        Room room = fixture.randomHuman(elapsedSeconds);
        rooms.add(room);
        return room;
    }

    private Game game(Room room) {
        return gameRegistry.find(room.sessionId()).orElseThrow();
    }

    private void register(Room room) {
        phaseScheduler.register(room.sessionId(), game(room).getStartedAt());
    }

    private DebateSession session(Long sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow();
    }

    private static Object phaseChanged(DebatePhase phase) {
        return argThat((Object event) -> event instanceof PhaseChangedEvent changed && changed.phase() == phase);
    }

    @Test
    @DisplayName("아무도 채팅하지 않아도 60초에 REVEAL 로 바뀌고 63초에 제출한 주장이 공개된다")
    void revealsWithoutChat() {
        Room room = startedRoom(57);
        Game game = game(room);
        LocalDateTime now = LocalDateTime.now();
        game.saveMemo(room.hostUserId(), "거짓말은 신뢰를 무너뜨린다", now);
        game.saveMemo(room.opponentUserId(), "선의의 거짓말도 있다", now);

        register(room);

        verify(gameMessageService, timeout(WAIT_MILLIS)).broadcastEvent(eq(room.sessionId()), phaseChanged(DebatePhase.REVEAL));
        waitUntil(() -> game.messagesAfter(0).size() == 2);
        assertThat(game.messagesAfter(0)).extracting(GameMessage::type)
                .containsExactly(MessageType.ARGUMENT, MessageType.ARGUMENT);
        assertThat(game.messagesAfter(0)).extracting(GameMessage::phase).containsOnly(DebatePhase.PREP);
    }

    @Test
    @DisplayName("230초에 FINAL_NOTICE(채팅 끝 260초)를 보내고, 이미 지난 구간 전환은 보내지 않는다")
    void finalNotice() {
        Room room = startedRoom(227);
        LocalDateTime chatEndsAt = game(room).getStartedAt().plusSeconds(260);

        register(room);

        verify(gameMessageService, timeout(WAIT_MILLIS)).broadcastEvent(eq(room.sessionId()), argThat(event ->
                event instanceof FinalNoticeEvent notice && notice.endsAt().toLocalDateTime().equals(chatEndsAt)));
        verify(gameMessageService, never()).broadcastEvent(eq(room.sessionId()), phaseChanged(DebatePhase.REVEAL));
        verify(gameMessageService, never()).broadcastEvent(eq(room.sessionId()), phaseChanged(DebatePhase.CHAT));
    }

    @Test
    @DisplayName("260초에 세션을 끝내고(COMPLETED) JUDGING · SESSION_FINISHED 를 보낸 뒤 새 방을 만들 수 있다")
    void finishesAt260() {
        Room room = startedRoom(257);

        register(room);

        verify(gameMessageService, timeout(WAIT_MILLIS)).broadcastEvent(eq(room.sessionId()), argThat(event ->
                event instanceof SessionFinishedEvent finished && finished.reason() == FinishReason.COMPLETED));
        verify(gameMessageService).broadcastEvent(eq(room.sessionId()), phaseChanged(DebatePhase.JUDGING));
        DebateSession session = session(room.sessionId());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session.getFinishReason()).isEqualTo(FinishReason.COMPLETED);
        assertThat(game(room).isFinished()).isTrue();
        // 판정이 대화를 읽어야 하므로 게임은 메모리에 남아 있습니다.
        assertThat(gameRegistry.find(room.sessionId())).isPresent();
        // 게임이 끝났으므로 ALREADY_IN_SESSION 에 걸리지 않습니다.
        Long newRoom = sessionMatchFacade.create(room.hostUserId(),
                new SessionCreateRequest(RoomType.RANDOM, fixture.topic().getId(), Stance.AGREE));
        sessionMatchFacade.cancel(room.hostUserId(), newRoom);
    }

    @Test
    @DisplayName("이미 끝난 게임이면 260초에 아무것도 하지 않는다")
    void skipsFinishedGame() {
        Room room = startedRoom(257);
        fixture.finish(room);

        register(room);

        verify(gameMessageService, after(WAIT_MILLIS).never()).broadcastEvent(eq(room.sessionId()),
                argThat(event -> event instanceof SessionFinishedEvent));
    }

    @Test
    @DisplayName("입장 · 바로 봇전 · AI 전환으로 게임이 만들어지면 시간표를 등록한다")
    void registersOnMatch() {
        Long hostId = fixture.newUser();
        Long joined = sessionMatchFacade.create(hostId,
                new SessionCreateRequest(RoomType.RANDOM, fixture.topic().getId(), Stance.AGREE));
        sessionMatchFacade.join(fixture.newUser(), joined);
        Long bot = sessionMatchFacade.startBot(fixture.newUser(),
                new BotMatchRequest(fixture.topic().getId(), Stance.DISAGREE));
        Long converterId = fixture.newUser();
        Long converted = sessionMatchFacade.create(converterId,
                new SessionCreateRequest(RoomType.RANDOM, fixture.topic().getId(), Stance.AGREE));
        sessionMatchFacade.convertToAi(converterId, converted);
        sessionIds.addAll(List.of(joined, bot, converted));

        for (Long sessionId : List.of(joined, bot, converted)) {
            verify(phaseScheduler).register(sessionId, session(sessionId).getStartedAt());
        }
    }

    /** 조건이 맞을 때까지 잠깐씩 기다립니다. 시간 안에 맞지 않으면 실패합니다. */
    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("시간 안에 조건이 맞지 않았습니다.");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
