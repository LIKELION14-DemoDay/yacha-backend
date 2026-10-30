package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.PreparedReplies;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.dto.PhaseChangedEvent;
import likelion.yacha_backend.domain.session.dto.SessionFinishedEvent;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameMessage;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.MessageType;
import likelion.yacha_backend.global.config.GameSchedulerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.messaging.MessageDeliveryException;

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
    /** 무작위 제출 시각(95~120초)이 지난 REBUTTAL */
    private static final long LATE_REBUTTAL = 135;
    /** 첫마디 시각(143초 + 0.5초)이 지난 CHAT */
    private static final long IN_CHAT = 150;
    /** 봇 실행기 스레드 수 ({@link GameSchedulerConfig#botExecutor}) */
    private static final int BOT_EXECUTOR_THREADS = 4;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private BotService botService;

    @Autowired
    private GameMemoService gameMemoService;

    @Autowired
    private SessionMatchFacade sessionMatchFacade;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private Clock clock;

    @Autowired
    @Qualifier(GameSchedulerConfig.BOT_EXECUTOR)
    private Executor botExecutor;

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

    /** {@code at} 에 공개된 봇 주장 원문. 공개를 일으키므로 테스트 마지막에 부릅니다. */
    private String botArgument(Game game, LocalDateTime at) {
        return game.revealArguments(at).stream()
                .filter(message -> message.type() == MessageType.ARGUMENT && message.phase() == DebatePhase.PREP)
                .filter(message -> message.participantId().equals(game.getAiParticipantId()))
                .map(GameMessage::content)
                .findFirst()
                .orElseThrow();
    }

    private void humanChat(Room room, String content) {
        gameMessageService.sendChat(room.sessionId(), room.hostUserId(), content);
        botService.humanSpoke(room.sessionId());
    }

    // ---- 주장 · 반론 ----

    @Test
    @DisplayName("봇전이 시작되면 봇 주장을 쓰기 시작한다 (AI 전환 경로)")
    void startsArgumentOnBotMatch() {
        Long userId = fixture.newUser();

        Long sessionId = startBot(sessionMatchFacade, fixture, userId, Stance.AGREE);
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
    @DisplayName("주장이 공개된 뒤라도 반론 구간(80초) 전에는 봇 반론을 쓰지 않는다 — 그때 다 쓰면 낼 수 없어 버려짐")
    void doesNotWriteRebuttalBeforeRebuttalPhase() {
        // 79초에 시작해 80초까지 지켜봅니다. 80초 전 어느 시각에 쓰기 시작해도 걸립니다.
        Room room = botRoom(79);

        botService.start(room.sessionId());

        verify(botWriter, after(1_000).never()).writeRebuttal(any(), any(), any());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("봇 반론은 85초에 쓰기 시작하고, 제출 시각 전에 다 써도 버리지 않는다")
    void writesRebuttalAt85(CapturedOutput output) {
        Room room = botRoom(84);
        Game game = game(room);

        botService.start(room.sessionId());

        verify(botWriter, timeout(WAIT_MILLIS)).writeRebuttal(any(), eq(Stance.DISAGREE), any());
        verify(gameMessageService, after(500).never())
                .broadcastSubmitted(eq(room.sessionId()), eq(game.getAiParticipantId()), eq(DebatePhase.REBUTTAL));
        assertThat(output.getAll()).doesNotContain("REBUTTAL 이 작성 구간을 넘겨 내지 않습니다");
    }

    @Test
    @DisplayName("사람이 먼저 내면 봇도 무작위 시각(20~50초)을 기다리지 않고 내고, 바로 REVEAL 로 넘어가 주장이 공개된다")
    void humanFirstAdvancesPrep() throws Exception {
        Room room = botRoom(10);
        Game game = game(room);
        botService.start(room.sessionId());
        verify(botWriter, timeout(WAIT_MILLIS)).writeArgument(any(), eq(Stance.DISAGREE));
        Thread.sleep(200);

        gameMemoService.saveMemo(room.sessionId(), room.hostUserId(), "거짓말은 신뢰를 무너뜨린다");

        // 봇 글이 준비돼 있으므로 2~5초 뒤에 냅니다.
        verify(gameMessageService, after(1_500).never())
                .broadcastSubmitted(eq(room.sessionId()), eq(game.getAiParticipantId()), eq(DebatePhase.PREP));
        verify(gameMessageService, timeout(WAIT_MILLIS)).broadcastEvent(eq(room.sessionId()), argThat(event ->
                event instanceof PhaseChangedEvent changed && changed.phase() == DebatePhase.REVEAL));
        assertThat(game.messagesAfter(0)).extracting(GameMessage::type, GameMessage::phase)
                .containsOnly(tuple(MessageType.ARGUMENT, DebatePhase.PREP)).hasSize(2);
        assertThat(game.timeline().version()).isEqualTo(1);
    }

    @Test
    @DisplayName("사람이 먼저 냈는데 봇 글이 아직 없으면, 임시 글 없이 다 쓰는 즉시 내고 넘어간다")
    void humanFirstBotNotReady() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            release.await();
            return "봇 주장";
        }).when(botWriter).writeArgument(any(), any());
        Room room = botRoom(10);
        Game game = game(room);
        botService.start(room.sessionId());
        verify(botWriter, timeout(WAIT_MILLIS)).writeArgument(any(), any());

        gameMemoService.saveMemo(room.sessionId(), room.hostUserId(), "거짓말은 신뢰를 무너뜨린다");
        verify(gameMessageService, after(500).never())
                .broadcastSubmitted(eq(room.sessionId()), eq(game.getAiParticipantId()), eq(DebatePhase.PREP));
        release.countDown();

        verify(gameMessageService, timeout(1_500)).broadcastEvent(eq(room.sessionId()), argThat(event ->
                event instanceof PhaseChangedEvent changed && changed.phase() == DebatePhase.REVEAL));
        assertThat(game.messagesAfter(0)).extracting(GameMessage::content)
                .containsExactly("거짓말은 신뢰를 무너뜨린다", "봇 주장");
    }

    @Test
    @DisplayName("주장이 일찍 넘어가면 반론 구간도 앞당겨지고(지금 + 20초), 봇은 반론 구간 전에 반론을 쓰지 않는다")
    void rebuttalFollowsAdvancedTimeline() {
        // 58초에 봇 · 사람이 다 내 2초 일찍 넘깁니다. 원래 반론 쓰기(85초)는 시간표 버전이 바뀌어 무시됩니다.
        Room room = botRoom(58);
        Game game = game(room);
        LocalDateTime now = LocalDateTime.now(clock);
        game.saveBotMemo("봇 주장", false, now);

        gameMemoService.saveMemo(room.sessionId(), room.hostUserId(), "거짓말은 신뢰를 무너뜨린다");

        assertThat(game.timeline().version()).isEqualTo(1);
        assertThat(game.timeline().startAt(DebatePhase.REBUTTAL)).isAfterOrEqualTo(now.plusSeconds(20));
        verify(gameMessageService).broadcastEvent(eq(room.sessionId()), argThat(event ->
                event instanceof PhaseChangedEvent changed && changed.phase() == DebatePhase.REVEAL));
        // 앞당긴 반론 구간은 지금부터 20초 뒤라 몇 초 안에는 쓰지 않습니다 (원래 85초 시각도 26초 뒤).
        verify(botWriter, after(1_000).never()).writeRebuttal(any(), any(), any());
    }

    @Test
    @DisplayName("실패가 이어지면 제출 시각에 임시 글을 내고, 작성 구간 끝까지 다시 시도한다")
    void placeholderWhileRetrying() {
        doThrow(new AiException("LINER_HTTP_ERROR_429", true)).when(botWriter).writeArgument(any(), any());
        Room room = botRoom(LATE_PREP);
        Game game = game(room);

        botService.start(room.sessionId());

        verify(botWriter, timeout(WAIT_MILLIS).atLeast(3)).writeArgument(any(), any());
        verify(gameMessageService, times(1))
                .broadcastSubmitted(eq(room.sessionId()), eq(game.getAiParticipantId()), eq(DebatePhase.PREP));
        assertThat(game.submittedParticipantIds(LocalDateTime.now(clock))).contains(game.getAiParticipantId());
    }

    @Test
    @DisplayName("다시 시도해 글이 끝나면 임시 글을 덮어쓰고, ARGUMENT_SUBMITTED 는 한 번만 보낸다")
    void textReplacesPlaceholder() {
        doThrow(new AiException("LINER_HTTP_ERROR_429", true)).doReturn("다시 써 낸 주장")
                .when(botWriter).writeArgument(any(), any());
        Room room = botRoom(LATE_PREP);
        Game game = game(room);

        botService.start(room.sessionId());

        verify(botWriter, timeout(WAIT_MILLIS).times(2)).writeArgument(any(), any());
        verify(gameMessageService, after(300).times(1))
                .broadcastSubmitted(eq(room.sessionId()), eq(game.getAiParticipantId()), eq(DebatePhase.PREP));
        assertThat(botArgument(game, game.getStartedAt().plusSeconds(64))).isEqualTo("다시 써 낸 주장");
    }

    @Test
    @DisplayName("작성 구간이 끝날 때까지 못 쓰면 실패 글을 공개하고 게임을 무효(ABORTED)로 끝낸다")
    void failureAbortsGame() {
        doThrow(new AiException("LINER_HTTP_ERROR_429", true)).when(botWriter).writeArgument(any(), any());
        // 작성 구간(60초)은 끝났고 공개(63초) 전인 유예 시간
        Room room = botRoom(61);

        botService.start(room.sessionId());

        verify(gameMessageService, timeout(WAIT_MILLIS)).broadcastEvent(room.sessionId(),
                SessionFinishedEvent.of(FinishReason.ABORTED));
        verify(gameMessageService, atLeastOnce()).broadcastAll(eq(room.sessionId()), argThat(messages -> messages.stream()
                .anyMatch(message -> message.type() == MessageType.ARGUMENT
                        && BotService.FAILURE_TEXT.equals(message.content()))));
        assertThat(gameRegistry.find(room.sessionId())).isEmpty();
        verify(botWriter, never()).writeRebuttal(any(), any(), anyString());
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
    void dropsLateArgument() throws Exception {
        Room room = botRoom(LATE_PREP);
        Game game = game(room);
        doAnswer(invocation -> {
            // 주장 공개(63초)가 지난 것처럼 공개를 먼저 일으킵니다.
            game.revealArguments(game.getStartedAt().plusSeconds(64));
            return "늦게 끝난 주장";
        }).when(botWriter).writeArgument(any(), any());

        botService.start(room.sessionId());

        verify(botWriter, timeout(WAIT_MILLIS)).writeArgument(any(), any());
        Thread.sleep(500);
        assertThat(game.messagesAfter(0)).noneMatch(message -> "늦게 끝난 주장".equals(message.content()));
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
    @DisplayName("대답을 만들지 못하면 대기 뒤 한 번 다시 시도해 그 대답을 보낸다")
    void retriesFailedReplyOnce() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doThrow(new AiException("LINER_HTTP_ERROR_429", true)).doReturn("이번엔 대답할게요.")
                .when(botWriter).reply(any(), any(), anyLong(), anyList());

        humanChat(room, "첫 번째");

        waitUntil(() -> botChats(game).size() == 1);
        assertThat(botChats(game).get(0).content()).isEqualTo("이번엔 대답할게요.");
        verify(botWriter, after(800).times(2)).reply(any(), any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("다시 시도해도 실패하면 준비된 문장으로 대답한다")
    void preparedReplyAfterRetryFails() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doThrow(new IllegalStateException("AI 응답 없음")).when(botWriter).reply(any(), any(), anyLong(), anyList());

        humanChat(room, "첫 번째");

        waitUntil(() -> botChats(game).size() == 1);
        assertThat(botChats(game).get(0).content()).isEqualTo(PreparedReplies.next(0));
        verify(botWriter, after(800).times(2)).reply(any(), any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("다시 시도할 수 없는 실패면 기다리지 않고 바로 준비된 문장으로 대답한다")
    void preparedReplyWithoutRetry() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doThrow(new AiException("LINER_HTTP_ERROR_401", false)).when(botWriter).reply(any(), any(), anyLong(), anyList());

        humanChat(room, "첫 번째");

        waitUntil(() -> botChats(game).size() == 1);
        assertThat(botChats(game).get(0).content()).isEqualTo(PreparedReplies.next(0));
        verify(botWriter, after(800).times(1)).reply(any(), any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("다시 시도를 기다리는 사이 사람이 또 말하면, 다시 시도하지 않고 새 말에 대답한다")
    void skipsRetryWhenHumanSpeaks() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doThrow(new IllegalStateException("AI 응답 없음")).doReturn("새 말에 대답할게요.")
                .when(botWriter).reply(any(), any(), anyLong(), anyList());

        humanChat(room, "첫 번째");
        verify(botWriter, timeout(WAIT_MILLIS).times(1)).reply(any(), any(), anyLong(), anyList());
        humanChat(room, "두 번째");

        waitUntil(() -> botChats(game).size() == 1);
        assertThat(botChats(game).get(0).content()).isEqualTo("새 말에 대답할게요.");
        verify(botWriter, after(800).times(2)).reply(any(), any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("다시 시도가 실행기에서 기다리는 사이 사람이 또 말하면, 그 시도는 AI 를 부르지 않고 새 말에 한 번만 대답한다")
    void skipsQueuedRetryWhenHumanSpeaks() throws Exception {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doThrow(new IllegalStateException("AI 응답 없음")).doReturn("새 말에 대답할게요.")
                .when(botWriter).reply(any(), any(), anyLong(), anyList());
        CountDownLatch release = new CountDownLatch(1);

        humanChat(room, "첫 번째");
        verify(botWriter, timeout(WAIT_MILLIS).times(1)).reply(any(), any(), anyLong(), anyList());
        try {
            // 실행기 스레드를 모두 잡아, 대답 재시도 대기(200ms) 뒤 다시 시도가 대기열에서 기다리게 합니다.
            for (int i = 0; i < BOT_EXECUTOR_THREADS; i++) {
                botExecutor.execute(() -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            Thread.sleep(400);
            humanChat(room, "두 번째");
            // 새 말의 대답 타이머(300ms)까지 울려, 끝나면 한 번 더 대답하도록 표시된 상태를 만듭니다.
            Thread.sleep(500);
        } finally {
            release.countDown();
        }

        waitUntil(() -> botChats(game).size() == 1);
        assertThat(botChats(game).get(0).content()).isEqualTo("새 말에 대답할게요.");
        verify(botWriter, after(800).times(2)).reply(any(), any(), anyLong(), anyList());
        assertThat(botChats(game)).hasSize(1);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("대답을 보내다 예상 밖 오류가 나면 어느 게임인지 로그로 남기고, 다음 말에는 다시 대답한다")
    void logsUnexpectedSendFailure(CapturedOutput output) {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doThrow(new MessageDeliveryException("보내기 실패")).doCallRealMethod()
                .when(gameMessageService).broadcastAll(eq(room.sessionId()), argThat(messages -> messages.stream()
                        .anyMatch(message -> message.participantId().equals(game.getAiParticipantId()))));

        humanChat(room, "첫 번째");
        waitUntil(() -> output.getAll().contains("[봇] 대답을 보내지 못했습니다. sessionId=" + room.sessionId()));
        humanChat(room, "두 번째");

        waitUntil(() -> botChats(game).size() == 2);
    }

    @Test
    @DisplayName("첫마디도 실패하면 한 번 다시 시도하고, 또 실패하면 준비된 문장으로 말한다")
    void preparedFirstMessage() {
        Room room = botRoom(IN_CHAT);
        Game game = game(room);
        doThrow(new IllegalStateException("AI 응답 없음")).when(botWriter).reply(any(), any(), anyLong(), anyList());

        botService.start(room.sessionId());

        waitUntil(() -> botChats(game).size() == 1);
        assertThat(botChats(game).get(0).content()).isEqualTo(PreparedReplies.next(0));
        verify(botWriter, after(800).times(2)).reply(any(), any(), anyLong(), anyList());
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
