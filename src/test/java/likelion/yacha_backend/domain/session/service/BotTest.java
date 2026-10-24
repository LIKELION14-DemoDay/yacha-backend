package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.dto.BotMatchRequest;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameMessage;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 봇 발언 (명세 2-4) — 주장 · 반론 제출, 채팅 대답 · 첫마디 · 상한 · 실패. 실제 타이머 · 실행기로 확인합니다
 * ({@link TimerTestSupport}, 대답 대기 300ms · 첫마디 대기 500ms). 무작위 제출 시각(20~50초 · 100~130초)이 이미 지난
 * 게임을 만들어, 쓰자마자 제출되게 합니다. 기본 봇 글은 임시 구현이고, 실패는 {@link #botWriter} 로 흉내 냅니다.
 */
@DisplayName("봇 발언 — 주장 · 반론 제출, 채팅 대답 · 첫마디 · 상한 · 실패")
class BotTest extends TimerTestSupport {

    private static final long WAIT_MILLIS = 5_000;
    /** 무작위 제출 시각(20~50초)이 지난 PREP */
    private static final long LATE_PREP = 55;
    /** 무작위 제출 시각(100~130초)이 지난 REBUTTAL */
    private static final long LATE_REBUTTAL = 135;
    /** 첫마디 시각(143초 + 0.5초)이 지난 CHAT */
    private static final long IN_CHAT = 150;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private BotService botService;

    @Autowired
    private SessionMatchFacade sessionMatchFacade;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private Clock clock;

    private final List<Long> sessionIds = new ArrayList<>();

    @AfterEach
    void removeGames() {
        sessionIds.forEach(gameRegistry::remove);
    }

    private Room botRoom(long elapsedSeconds) {
        Room room = fixture.memberBot(elapsedSeconds);
        sessionIds.add(room.sessionId());
        return room;
    }

    private Game game(Room room) {
        return gameRegistry.find(room.sessionId()).orElseThrow();
    }

    private List<GameMessage> botChats(Game game) {
        return game.messagesAfter(0).stream()
                .filter(message -> message.type() == MessageType.CHAT)
                .filter(message -> message.participantId().equals(game.getAiParticipantId()))
                .toList();
    }

    private void humanChat(Room room, String content) {
        gameMessageService.sendChat(room.sessionId(), room.hostUserId(), content);
        botService.humanSpoke(room.sessionId());
    }

    // ---- 주장 · 반론 ----

    @Test
    @DisplayName("봇전이 시작되면 봇 주장을 쓰기 시작한다 (바로 봇전 경로)")
    void startsArgumentOnBotMatch() {
        Long userId = fixture.newUser();

        Long sessionId = sessionMatchFacade.startBot(userId, new BotMatchRequest(fixture.topic().getId(), Stance.AGREE));
        sessionIds.add(sessionId);

        verify(botWriter, timeout(WAIT_MILLIS)).writeArgument(any(), eq(Stance.DISAGREE));
    }

    @Test
    @DisplayName("제출 시각이 지났으면 쓰자마자 봇 주장을 내고 ARGUMENT_SUBMITTED 를 보낸다")
    void submitsArgument() {
        Room room = botRoom(LATE_PREP);
        Game game = game(room);

        botService.start(room.sessionId());

        verify(gameMessageService, timeout(WAIT_MILLIS))
                .broadcastSubmitted(eq(room.sessionId()), eq(game.getAiParticipantId()), eq(DebatePhase.PREP));
        assertThat(game.submittedParticipantIds(LocalDateTime.now(clock))).contains(game.getAiParticipantId());
    }

    @Test
    @DisplayName("봇 반론은 공개된 사람 주장 원문을 보고 쓰고, 제출 시각이 지났으면 바로 낸다")
    void submitsRebuttalWithHumanArgument() {
        Room room = botRoom(LATE_REBUTTAL);
        Game game = game(room);
        game.saveMemo(room.hostUserId(), "거짓말은 신뢰를 무너뜨린다", game.getStartedAt().plusSeconds(10));

        botService.start(room.sessionId());

        verify(botWriter, timeout(WAIT_MILLIS)).writeRebuttal(any(), eq(Stance.DISAGREE), eq("거짓말은 신뢰를 무너뜨린다"));
        verify(gameMessageService, timeout(WAIT_MILLIS))
                .broadcastSubmitted(eq(room.sessionId()), eq(game.getAiParticipantId()), eq(DebatePhase.REBUTTAL));
        verify(botWriter, never()).writeArgument(any(), any());
    }

    @Test
    @DisplayName("주장 · 반론은 2번 시도하고, 모두 실패하면 내지 않는다")
    void argumentFailsAfterMaxAttempts() {
        doThrow(new IllegalStateException("AI 응답 없음")).when(botWriter).writeArgument(any(), any());
        Room room = botRoom(LATE_PREP);

        botService.start(room.sessionId());

        verify(botWriter, timeout(WAIT_MILLIS).times(2)).writeArgument(any(), any());
        verify(gameMessageService, after(500).never()).broadcastSubmitted(eq(room.sessionId()), anyLong(), any());
    }

    @Test
    @DisplayName("다시 시도할 수 없는 실패(AiException retryable=false)면 한 번만 부른다")
    void argumentStopsOnNonRetryable() {
        doThrow(new AiException("LINER_HTTP_ERROR_401", false)).when(botWriter).writeArgument(any(), any());
        Room room = botRoom(LATE_PREP);

        botService.start(room.sessionId());

        verify(botWriter, after(500).times(1)).writeArgument(any(), any());
    }

    @Test
    @DisplayName("공개 시각을 넘겨 끝난 글은 내지 않는다")
    void dropsLateArgument() {
        Room room = botRoom(LATE_PREP);
        Game game = game(room);
        doAnswer(invocation -> {
            // 주장 공개(63초)가 지난 것처럼 공개를 먼저 일으킵니다.
            game.revealArguments(game.getStartedAt().plusSeconds(64));
            return "늦게 끝난 주장";
        }).when(botWriter).writeArgument(any(), any());

        botService.start(room.sessionId());

        verify(botWriter, timeout(WAIT_MILLIS)).writeArgument(any(), any());
        verify(gameMessageService, after(500).never()).broadcastSubmitted(eq(room.sessionId()), anyLong(), any());
    }

    // ---- 채팅 ----

    @Test
    @DisplayName("사람이 말하면 대답 대기 뒤 봇이 CHAT 으로 대답한다")
    void repliesAfterDelay() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);

        humanChat(room, "그건 왜 그렇죠?");

        waitUntil(() -> botChats(game).size() == 1);
        verify(botWriter).reply(any(), eq(Stance.DISAGREE), eq(game.getAiParticipantId()), anyList());
    }

    @Test
    @DisplayName("대답 대기 안에 여러 번 말하면 마지막 말 뒤 한 번만 대답한다")
    void repliesOnceForBurst() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);

        humanChat(room, "첫 번째");
        humanChat(room, "두 번째");
        humanChat(room, "세 번째");

        waitUntil(() -> botChats(game).size() == 1);
        verify(botWriter, after(800).times(1)).reply(any(), any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("대답을 만드는 중에 사람이 또 말하면, 지금 대답을 보낸 뒤 한 번 더 대답한다")
    void repliesAgainAfterInFlight() throws Exception {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doAnswer(invocation -> {
            Thread.sleep(800);
            return "잠깐 생각해 볼게요.";
        }).doReturn("그 말은 맞지 않아요.").when(botWriter).reply(any(), any(), anyLong(), anyList());

        humanChat(room, "첫 번째");
        Thread.sleep(500);
        humanChat(room, "두 번째");

        waitUntil(() -> botChats(game).size() == 2);
        verify(botWriter, times(2)).reply(any(), any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("채팅이 열리고 사람이 조용하면 봇이 첫마디를 한 번 먼저 말한다")
    void firstMessageWhenSilent() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);

        botService.start(room.sessionId());

        waitUntil(() -> botChats(game).size() == 1);
        verify(botWriter, after(800).times(1)).reply(any(), any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("사람이 먼저 말했으면 첫마디는 하지 않고 대답만 한다")
    void noFirstMessageAfterHumanChat() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        humanChat(room, "제가 먼저 말할게요");

        botService.start(room.sessionId());

        waitUntil(() -> botChats(game).size() == 1);
        verify(botWriter, after(800).times(1)).reply(any(), any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("봇 채팅이 상한(30개)에 이르면 더 대답하지 않는다")
    void stopsAtMaxReplies() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        LocalDateTime now = LocalDateTime.now(clock);
        for (int i = 0; i < 30; i++) {
            game.appendBotChat("봇 채팅 " + i, now);
        }

        humanChat(room, "또 대답해 줄래요?");

        verify(botWriter, after(800).never()).reply(any(), any(), anyLong(), anyList());
        assertThat(game.botChatCount()).isEqualTo(30);
    }

    @Test
    @DisplayName("대답을 만들지 못하면 그 대답만 건너뛰고, 다음 말에는 다시 대답한다")
    void skipsFailedReply() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doThrow(new IllegalStateException("AI 응답 없음")).doReturn("이번엔 대답할게요.")
                .when(botWriter).reply(any(), any(), anyLong(), anyList());

        humanChat(room, "첫 번째");
        verify(botWriter, timeout(WAIT_MILLIS).times(1)).reply(any(), any(), anyLong(), anyList());
        humanChat(room, "두 번째");

        waitUntil(() -> botChats(game).size() == 1);
        verify(botWriter, times(2)).reply(any(), any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("대답을 만드는 사이 게임이 끝나면 대답을 버린다")
    void dropsReplyAfterFinish() throws Exception {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doAnswer(invocation -> {
            Thread.sleep(500);
            return "늦은 대답";
        }).when(botWriter).reply(any(), any(), anyLong(), anyList());

        humanChat(room, "첫 번째");
        verify(botWriter, timeout(WAIT_MILLIS)).reply(any(), any(), anyLong(), anyList());
        fixture.finish(room);

        Thread.sleep(800);
        assertThat(botChats(game)).isEmpty();
    }

    @Test
    @DisplayName("사람전에서는 봇이 아무것도 하지 않는다")
    void humanMatchIgnored() {
        Room room = fixture.randomHuman(IN_CHAT);
        sessionIds.add(room.sessionId());

        botService.start(room.sessionId());
        gameMessageService.sendChat(room.sessionId(), room.hostUserId(), "안녕하세요");
        botService.humanSpoke(room.sessionId());

        verify(botWriter, after(800).never()).reply(any(), any(), anyLong(), anyList());
        verify(botWriter, never()).writeArgument(any(), any());
        verify(botWriter, never()).writeRebuttal(any(), any(), anyString());
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
