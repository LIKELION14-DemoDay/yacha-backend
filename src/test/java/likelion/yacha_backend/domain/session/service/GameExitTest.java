package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.dto.OpponentDisconnectedEvent;
import likelion.yacha_backend.domain.session.dto.OpponentReconnectedEvent;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.dto.SessionFinishedEvent;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateResult;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 게임 중 종료 — 나가기 · 연결 끊김 몰수패 · 복귀 · 양쪽 동시 이탈 (명세 2-11). 실제 타이머로 확인합니다 ({@link TimerTestSupport},
 * 이탈 유예 1초). 연결 이벤트는 {@link GameConnectionService} 의 처리 메서드를 직접 부르고, 실제 STOMP 끊김은
 * {@code GameStompTest} 가 확인합니다.
 */
@DisplayName("게임 중 종료 — 나가기 · 끊김 몰수패 · 복귀 · 동시 이탈")
class GameExitTest extends TimerTestSupport {

    private static final long WAIT_MILLIS = 5_000;
    /** 이탈 유예(1초)가 충분히 지나는 시간 */
    private static final long AFTER_GRACE_MILLIS = 1_800;
    private static final long IN_CHAT = 150;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private GameConnectionService gameConnectionService;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private SessionMatchFacade sessionMatchFacade;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final List<Room> rooms = new ArrayList<>();

    @AfterEach
    void removeGames() {
        rooms.forEach(fixture::removeGame);
    }

    private Room track(Room room) {
        rooms.add(room);
        return room;
    }

    private ResultActions leave(Long userId, Long sessionId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/leave").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtTokenProvider.createAccessToken(userId, Role.USER)));
    }

    private DebateSession session(Long sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow();
    }

    private DebateParticipant participant(Long participantId) {
        return participantRepository.findById(participantId).orElseThrow();
    }

    private DebateResult aiResult(Room room) {
        return participantRepository.findAllBySession_Id(room.sessionId()).stream()
                .filter(DebateParticipant::isAi)
                .findFirst().orElseThrow()
                .getResult();
    }

    private void verifyFinished(Room room, FinishReason reason) {
        verify(gameMessageService, timeout(WAIT_MILLIS)).broadcastEvent(eq(room.sessionId()), argThat(event ->
                event instanceof SessionFinishedEvent finished && finished.reason() == reason));
        DebateSession session = session(room.sessionId());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(session.getFinishReason()).isEqualTo(reason);
        // 판정이 없으므로 게임은 바로 메모리에서 지웁니다.
        assertThat(gameRegistry.find(room.sessionId())).isEmpty();
    }

    /** 두 사람 모두 토론방을 구독한 상태로 만듭니다. */
    private void subscribeBoth(Room room) {
        gameConnectionService.subscribed(room.sessionId(), room.hostUserId(), "host-1");
        gameConnectionService.subscribed(room.sessionId(), room.opponentUserId(), "opponent-1");
    }

    // ---- 나가기 ----

    @Test
    @DisplayName("나가면 바로 FORFEIT — 나간 사람 LOSE · 남은 사람 WIN, 게임을 지우고 SESSION_FINISHED 를 보낸다")
    void leaveForfeits() throws Exception {
        Room room = track(fixture.members(IN_CHAT));

        leave(room.hostUserId(), room.sessionId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(room.sessionId()));

        verifyFinished(room, FinishReason.FORFEIT);
        assertThat(participant(room.hostParticipantId()).getResult()).isEqualTo(DebateResult.LOSE);
        assertThat(participant(room.opponentParticipantId()).getResult()).isEqualTo(DebateResult.WIN);
    }

    @Test
    @DisplayName("나간 뒤 결과 조회는 판정 없이 DB 승패만 주고, 바로 새 방을 만들 수 있다")
    void resultAndNewRoomAfterLeave() throws Exception {
        Room room = track(fixture.members(IN_CHAT));
        leave(room.hostUserId(), room.sessionId()).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/sessions/" + room.sessionId() + "/result").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtTokenProvider.createAccessToken(room.opponentUserId(), Role.USER)))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.finishReason").value("FORFEIT"))
                .andExpect(jsonPath("$.data.winnerParticipantId").value(room.opponentParticipantId()))
                .andExpect(jsonPath("$.data.criteria").doesNotExist());
        Long newRoom = sessionMatchFacade.create(room.hostUserId(),
                new SessionCreateRequest(RoomType.RANDOM, fixture.topic().getId(), Stance.AGREE));
        sessionMatchFacade.cancel(room.hostUserId(), newRoom);
    }

    @Test
    @DisplayName("게스트는 승패를 기록하지 않는다 — 나간 회원 LOSE, 남은 게스트 NULL")
    void guestNotRecorded() throws Exception {
        Room room = track(fixture.memberVsGuest(IN_CHAT));

        leave(room.hostUserId(), room.sessionId()).andExpect(status().isOk());

        assertThat(participant(room.hostParticipantId()).getResult()).isEqualTo(DebateResult.LOSE);
        assertThat(participant(room.opponentParticipantId()).getResult()).isNull();
    }

    @Test
    @DisplayName("봇전에서 사람이 나가면 AI 가 WIN 으로 기록된다")
    void botMatchLeave() throws Exception {
        Room room = track(fixture.memberBot(IN_CHAT));

        leave(room.hostUserId(), room.sessionId()).andExpect(status().isOk());

        verifyFinished(room, FinishReason.FORFEIT);
        assertThat(participant(room.hostParticipantId()).getResult()).isEqualTo(DebateResult.LOSE);
        assertThat(aiResult(room)).isEqualTo(DebateResult.WIN);
    }

    @Test
    @DisplayName("참가자가 아니면 NOT_PARTICIPANT, 끝났거나 대기 중이면 SESSION_NOT_IN_PROGRESS, 없으면 SESSION_NOT_FOUND")
    void leaveErrors() throws Exception {
        Room room = track(fixture.members(IN_CHAT));
        leave(fixture.newUser(), room.sessionId()).andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));

        leave(room.hostUserId(), room.sessionId()).andExpect(status().isOk());
        leave(room.opponentUserId(), room.sessionId())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_IN_PROGRESS"));

        Room waiting = fixture.waitingRandom();
        leave(waiting.hostUserId(), waiting.sessionId())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_IN_PROGRESS"));
        sessionMatchFacade.cancel(waiting.hostUserId(), waiting.sessionId());
        leave(room.hostUserId(), 999_999L).andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    // ---- 연결 끊김 ----

    @Test
    @DisplayName("끊기면 disconnected_at 을 기록하고 OPPONENT_DISCONNECTED 를 보낸 뒤, 유예가 지나면 FORFEIT — 끊긴 사람 LOSE")
    void disconnectForfeits() {
        Room room = track(fixture.members(IN_CHAT));
        subscribeBoth(room);

        gameConnectionService.disconnected(room.sessionId(), room.hostUserId(), "host-1");

        verify(gameMessageService).broadcastEvent(eq(room.sessionId()),
                argThat(event -> event instanceof OpponentDisconnectedEvent));
        assertThat(participant(room.hostParticipantId()).getDisconnectedAt()).isNotNull();
        verifyFinished(room, FinishReason.FORFEIT);
        assertThat(participant(room.hostParticipantId()).getResult()).isEqualTo(DebateResult.LOSE);
        assertThat(participant(room.opponentParticipantId()).getResult()).isEqualTo(DebateResult.WIN);
    }

    @Test
    @DisplayName("유예 안에 다시 구독하면 복귀 — OPPONENT_RECONNECTED, disconnected_at NULL, 유예가 지나도 끝나지 않는다")
    void reconnectWithinGrace() {
        Room room = track(fixture.members(IN_CHAT));
        subscribeBoth(room);
        gameConnectionService.disconnected(room.sessionId(), room.hostUserId(), "host-1");

        gameConnectionService.subscribed(room.sessionId(), room.hostUserId(), "host-2");

        verify(gameMessageService).broadcastEvent(eq(room.sessionId()),
                argThat(event -> event instanceof OpponentReconnectedEvent));
        assertThat(participant(room.hostParticipantId()).getDisconnectedAt()).isNull();
        verify(gameMessageService, after(AFTER_GRACE_MILLIS).never()).broadcastEvent(eq(room.sessionId()),
                argThat(event -> event instanceof SessionFinishedEvent));
        assertThat(session(room.sessionId()).getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("새 연결이 먼저 구독한 뒤 예전 연결이 끊기면(새로고침) 무시한다")
    void ignoresOldConnection() {
        Room room = track(fixture.members(IN_CHAT));
        subscribeBoth(room);
        gameConnectionService.subscribed(room.sessionId(), room.hostUserId(), "host-2");

        gameConnectionService.disconnected(room.sessionId(), room.hostUserId(), "host-1");

        verify(gameMessageService, after(AFTER_GRACE_MILLIS).never()).broadcastEvent(eq(room.sessionId()),
                argThat(event -> event instanceof OpponentDisconnectedEvent || event instanceof SessionFinishedEvent));
        assertThat(participant(room.hostParticipantId()).getDisconnectedAt()).isNull();
    }

    @Test
    @DisplayName("대기 중에 두 연결로 구독했으면 매칭 뒤 가장 최근 연결만 인정 — 예전 연결이 끊기면 무시, 최근 연결이 끊기면 유예")
    void latestSubscriptionBeforeGame() {
        Room waiting = track(fixture.waitingRandom());
        gameConnectionService.subscribed(waiting.sessionId(), waiting.hostUserId(), "host-1");
        gameConnectionService.subscribed(waiting.sessionId(), waiting.hostUserId(), "host-2");
        sessionMatchFacade.join(fixture.newUser(), waiting.sessionId());

        gameConnectionService.disconnected(waiting.sessionId(), waiting.hostUserId(), "host-1");
        verify(gameMessageService, after(300).never()).broadcastEvent(eq(waiting.sessionId()),
                argThat(event -> event instanceof OpponentDisconnectedEvent));

        gameConnectionService.disconnected(waiting.sessionId(), waiting.hostUserId(), "host-2");
        verify(gameMessageService).broadcastEvent(eq(waiting.sessionId()),
                argThat(event -> event instanceof OpponentDisconnectedEvent));
    }

    @Test
    @DisplayName("유예가 끝날 때 상대도 끊겨 있으면 ABORTED — 승패 NULL")
    void bothDisconnectedAborts() {
        Room room = track(fixture.members(IN_CHAT));
        subscribeBoth(room);

        gameConnectionService.disconnected(room.sessionId(), room.hostUserId(), "host-1");
        gameConnectionService.disconnected(room.sessionId(), room.opponentUserId(), "opponent-1");

        verifyFinished(room, FinishReason.ABORTED);
        assertThat(participant(room.hostParticipantId()).getResult()).isNull();
        assertThat(participant(room.opponentParticipantId()).getResult()).isNull();
    }

    @Test
    @DisplayName("봇전에서 사람이 끊긴 채 유예가 지나면 FORFEIT — AI WIN")
    void botMatchDisconnect() {
        Room room = track(fixture.memberBot(IN_CHAT));
        gameConnectionService.subscribed(room.sessionId(), room.hostUserId(), "host-1");

        gameConnectionService.disconnected(room.sessionId(), room.hostUserId(), "host-1");

        verifyFinished(room, FinishReason.FORFEIT);
        assertThat(aiResult(room)).isEqualTo(DebateResult.WIN);
    }

    @Test
    @DisplayName("나가기로 이미 끝난 뒤 유예가 끝나도 아무것도 하지 않는다")
    void graceAfterLeave() throws Exception {
        Room room = track(fixture.members(IN_CHAT));
        subscribeBoth(room);
        gameConnectionService.disconnected(room.sessionId(), room.opponentUserId(), "opponent-1");

        leave(room.hostUserId(), room.sessionId()).andExpect(status().isOk());

        verify(gameMessageService, after(AFTER_GRACE_MILLIS)).broadcastEvent(eq(room.sessionId()),
                argThat(event -> event instanceof SessionFinishedEvent));
        assertThat(participant(room.hostParticipantId()).getResult()).isEqualTo(DebateResult.LOSE);
        assertThat(participant(room.opponentParticipantId()).getResult()).isEqualTo(DebateResult.WIN);
        assertThat(session(room.sessionId()).getFinishReason()).isEqualTo(FinishReason.FORFEIT);
    }

    @Test
    @DisplayName("관전자 · 구독한 적 없는 연결이 끊겨도 아무것도 하지 않는다")
    void ignoresUnknownConnections() {
        Room room = track(fixture.members(IN_CHAT));
        Long spectator = fixture.newUser();
        gameConnectionService.subscribed(room.sessionId(), spectator, "spectator-1");

        gameConnectionService.disconnected(room.sessionId(), spectator, "spectator-1");
        gameConnectionService.disconnected(room.sessionId(), room.hostUserId(), "never-subscribed");

        verify(gameMessageService, after(AFTER_GRACE_MILLIS).never()).broadcastEvent(eq(room.sessionId()),
                argThat(event -> event instanceof OpponentDisconnectedEvent || event instanceof SessionFinishedEvent));
    }
}
