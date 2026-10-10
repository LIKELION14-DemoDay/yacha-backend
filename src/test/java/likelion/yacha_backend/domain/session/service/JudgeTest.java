package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import likelion.yacha_backend.domain.ai.AiMessage;
import likelion.yacha_backend.domain.ai.JudgeCriterion;
import likelion.yacha_backend.domain.ai.JudgeRequest;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateResult;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.JudgeStatus;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 판정 · 결과 조회 — 실제 판정 실행기 · 타이머로 확인합니다 ({@link TimerTestSupport}, 결과 보관 시간 2초).
 * 점수는 {@code Judge} 스파이로 정해 승 · 패 · 무를 고릅니다.
 */
@DisplayName("판정 · 결과 — 승패 기록 · 재판정 · 보관 시간 · GET /result")
class JudgeTest extends TimerTestSupport {

    private static final long WAIT_MILLIS = 6_000;
    /** 진행 중인 게임. 260초 종료 대신 {@link SessionFixture#finish} 로 끝냅니다 */
    private static final long IN_CHAT = 150;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private JudgeService judgeService;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final List<Room> rooms = new ArrayList<>();

    @AfterEach
    void removeGames() {
        rooms.forEach(fixture::removeGame);
    }

    private Room finished(Room room) {
        rooms.add(room);
        fixture.finish(room);
        return room;
    }

    private Game game(Room room) {
        return gameRegistry.find(room.sessionId()).orElseThrow();
    }

    private ResultActions result(Long userId, Room room) throws Exception {
        return mockMvc.perform(get("/api/v1/sessions/" + room.sessionId() + "/result")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(userId, Role.USER)));
    }

    private String judgeStatusOf(Long userId, Room room) throws Exception {
        String body = result(userId, room).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.status");
    }

    /** {@code first} 참가자는 기준마다 {@code firstScore}, 나머지 참가자는 {@code otherScore} 점. */
    private void scores(Long first, int firstScore, int otherScore) {
        doAnswer(invocation -> {
            JudgeRequest request = invocation.getArgument(0);
            return new JudgeResult(request.participants().stream()
                    .map(p -> new JudgeResult.ParticipantScore(p.participantId(),
                            each(p.participantId().equals(first) ? firstScore : otherScore)))
                    .toList(), summaries());
        }).when(judge).judge(any());
    }

    private DebateResult recorded(Long participantId) {
        return participantRepository.findById(participantId).orElseThrow().getResult();
    }

    @Test
    @DisplayName("판정이 끝나면 READY — 기준별 점수 · 요약 · 승패를 주고, 회원은 기록 · 게스트는 기록하지 않는다")
    void judgesAndRecords() throws Exception {
        Room room = finished(fixture.memberVsGuest(IN_CHAT));
        scores(room.hostParticipantId(), 20, 15);

        judgeService.start(room.sessionId());

        waitUntil(() -> game(room).judgeStatus() == JudgeStatus.READY);
        result(room.hostUserId(), room)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.finishReason").value("COMPLETED"))
                .andExpect(jsonPath("$.data.winnerParticipantId").value(room.hostParticipantId()))
                .andExpect(jsonPath("$.data.criteria.length()").value(4))
                .andExpect(jsonPath("$.data.criteria[0].key").value("LOGIC"))
                .andExpect(jsonPath("$.data.criteria[0].summary").value("LOGIC 요약"))
                .andExpect(jsonPath("$.data.criteria[3].key").value("CONSISTENCY"))
                .andExpect(jsonPath("$.data.participants.length()").value(2))
                .andExpect(jsonPath("$.data.participants[0].participantId").value(room.hostParticipantId()))
                .andExpect(jsonPath("$.data.participants[0].isMe").value(true))
                .andExpect(jsonPath("$.data.participants[0].stance").value("AGREE"))
                .andExpect(jsonPath("$.data.participants[0].result").value("WIN"))
                .andExpect(jsonPath("$.data.participants[0].scores.LOGIC").value(20))
                .andExpect(jsonPath("$.data.participants[0].total").value(80))
                .andExpect(jsonPath("$.data.participants[0].philosopherType").doesNotExist())
                .andExpect(jsonPath("$.data.participants[1].isMe").value(false))
                .andExpect(jsonPath("$.data.participants[1].result").value("LOSE"))
                .andExpect(jsonPath("$.data.participants[1].total").value(60));
        // 게스트도 보관 시간 안에는 자기 결과를 봅니다.
        result(room.opponentUserId(), room)
                .andExpect(jsonPath("$.data.participants[1].isMe").value(true))
                .andExpect(jsonPath("$.data.participants[1].result").value("LOSE"));

        assertThat(recorded(room.hostParticipantId())).isEqualTo(DebateResult.WIN);
        assertThat(recorded(room.opponentParticipantId())).isNull();
    }

    @Test
    @DisplayName("공개된 주장 · 반론과 채팅 전체를 순서대로 판정에 넘긴다")
    void passesConversation() {
        Room room = fixture.memberVsGuest(IN_CHAT);
        rooms.add(room);
        Game game = game(room);
        LocalDateTime now = LocalDateTime.now();
        game.revealArguments(now);
        game.appendChat(room.hostUserId(), "첫 채팅", now);
        game.appendChat(room.opponentUserId(), "두 번째 채팅", now);
        fixture.finish(room);
        ArgumentCaptor<JudgeRequest> captor = ArgumentCaptor.forClass(JudgeRequest.class);

        judgeService.start(room.sessionId());

        waitUntil(() -> game.judgeStatus() == JudgeStatus.READY);
        verify(judge).judge(captor.capture());
        JudgeRequest request = captor.getValue();
        assertThat(request.topic().statement()).isEqualTo("거짓말은 언제나 나쁜가?");
        assertThat(request.participants()).extracting(JudgeRequest.Participant::participantId)
                .containsExactlyInAnyOrder(room.hostParticipantId(), room.opponentParticipantId());
        assertThat(request.messages()).extracting(AiMessage::participantId, AiMessage::kind, AiMessage::content)
                .containsExactly(
                        tuple(room.hostParticipantId(), AiMessage.Kind.CHAT, "첫 채팅"),
                        tuple(room.opponentParticipantId(), AiMessage.Kind.CHAT, "두 번째 채팅"));
    }

    @Test
    @DisplayName("봇전은 사용자와 AI 승패를 모두 기록하고, 봇은 닉네임이 없다")
    void botMatchRecordsAi() throws Exception {
        Room room = finished(fixture.memberBot(IN_CHAT));
        DebateParticipant ai = participantRepository.findAllBySession_Id(room.sessionId()).stream()
                .filter(DebateParticipant::isAi).findFirst().orElseThrow();
        scores(ai.getId(), 25, 10);

        judgeService.start(room.sessionId());

        waitUntil(() -> game(room).judgeStatus() == JudgeStatus.READY);
        assertThat(recorded(ai.getId())).isEqualTo(DebateResult.WIN);
        assertThat(recorded(room.hostParticipantId())).isEqualTo(DebateResult.LOSE);
        result(room.hostUserId(), room)
                .andExpect(jsonPath("$.data.winnerParticipantId").value(ai.getId()))
                .andExpect(jsonPath("$.data.participants[1].nickname").doesNotExist());
    }

    @Test
    @DisplayName("총점이 같으면 둘 다 DRAW, 승자는 null")
    void draw() throws Exception {
        Room room = finished(fixture.memberVsGuest(IN_CHAT));
        scores(room.hostParticipantId(), 18, 18);

        judgeService.start(room.sessionId());

        waitUntil(() -> game(room).judgeStatus() == JudgeStatus.READY);
        result(room.hostUserId(), room)
                .andExpect(jsonPath("$.data.winnerParticipantId").doesNotExist())
                .andExpect(jsonPath("$.data.participants[0].result").value("DRAW"))
                .andExpect(jsonPath("$.data.participants[1].result").value("DRAW"));
        assertThat(recorded(room.hostParticipantId())).isEqualTo(DebateResult.DRAW);
    }

    @Test
    @DisplayName("판정 중이면 PENDING")
    void pending() throws Exception {
        Room room = finished(fixture.memberVsGuest(IN_CHAT));
        doAnswer(invocation -> {
            Thread.sleep(1_000);
            return invocation.callRealMethod();
        }).when(judge).judge(any());

        judgeService.start(room.sessionId());

        assertThat(judgeStatusOf(room.hostUserId(), room)).isEqualTo("PENDING");
        waitUntil(() -> game(room).judgeStatus() == JudgeStatus.READY);
    }

    @Test
    @DisplayName("3번 모두 실패하면 FAILED, 결과를 다시 조회하면 다시 판정한다")
    void retriesAfterFailure() throws Exception {
        Room room = finished(fixture.memberVsGuest(IN_CHAT));
        doThrow(new IllegalStateException("AI 응답 없음")).when(judge).judge(any());

        judgeService.start(room.sessionId());

        waitUntil(() -> game(room).judgeStatus() == JudgeStatus.FAILED);
        verify(judge, times(3)).judge(any());
        assertThat(recorded(room.hostParticipantId())).isNull();

        scores(room.hostParticipantId(), 20, 10);
        assertThat(judgeStatusOf(room.hostUserId(), room)).isEqualTo("PENDING");
        waitUntil(() -> game(room).judgeStatus() == JudgeStatus.READY);
        assertThat(recorded(room.hostParticipantId())).isEqualTo(DebateResult.WIN);
    }

    @Test
    @DisplayName("보관 시간이 지나면 게임을 지우고 승패만 준다 — 기록이 없는 게스트 쪽은 null")
    void afterRetention() throws Exception {
        Room room = finished(fixture.memberVsGuest(IN_CHAT));
        scores(room.hostParticipantId(), 10, 20);

        judgeService.start(room.sessionId());

        waitUntil(() -> gameRegistry.find(room.sessionId()).isEmpty());
        result(room.hostUserId(), room)
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.winnerParticipantId").value(room.opponentParticipantId()))
                .andExpect(jsonPath("$.data.criteria").doesNotExist())
                .andExpect(jsonPath("$.data.participants[0].result").value("LOSE"))
                .andExpect(jsonPath("$.data.participants[0].scores").doesNotExist())
                .andExpect(jsonPath("$.data.participants[0].total").doesNotExist())
                .andExpect(jsonPath("$.data.participants[1].result").doesNotExist());
    }

    @Test
    @DisplayName("판정 전에 게임이 사라지면(서버 재시작) FAILED 이고 승패는 NULL 로 남는다")
    void lostBeforeJudging() throws Exception {
        Room room = finished(fixture.memberVsGuest(IN_CHAT));
        fixture.removeGame(room);

        result(room.hostUserId(), room)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.finishReason").value("COMPLETED"));
        assertThat(recorded(room.hostParticipantId())).isNull();
    }

    @Test
    @DisplayName("참가자가 아니면 NOT_PARTICIPANT, 끝나지 않은 토론이면 SESSION_NOT_FINISHED")
    void errors() throws Exception {
        Room room = fixture.memberVsGuest(IN_CHAT);
        rooms.add(room);

        result(fixture.newUser(), room)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));
        result(room.hostUserId(), room)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FINISHED"));
    }

    private static Map<JudgeCriterion, Integer> each(int score) {
        Map<JudgeCriterion, Integer> scores = new EnumMap<>(JudgeCriterion.class);
        for (JudgeCriterion criterion : JudgeCriterion.values()) {
            scores.put(criterion, score);
        }
        return scores;
    }

    private static Map<JudgeCriterion, String> summaries() {
        Map<JudgeCriterion, String> summaries = new EnumMap<>(JudgeCriterion.class);
        for (JudgeCriterion criterion : JudgeCriterion.values()) {
            summaries.put(criterion, criterion.name() + " 요약");
        }
        return summaries;
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
