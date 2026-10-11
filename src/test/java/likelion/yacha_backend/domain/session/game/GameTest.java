package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Game — 게임 메모리 상태")
class GameTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 10, 31, 12, 0, 0);
    private static final long ALICE = 1L;
    private static final long BOB = 2L;
    private static final long SPECTATOR = 99L;
    private static final long ALICE_PARTICIPANT = 11L;
    private static final long BOB_PARTICIPANT = 12L;

    /** 시간표: PREP 0~60 · REVEAL 60~80 · REBUTTAL 80~140 · CHAT 140~260 · JUDGING 260~, 제출 유예 3초 */
    private static final LocalDateTime PREP = STARTED_AT.plusSeconds(10);
    private static final LocalDateTime PREP_GRACE = STARTED_AT.plusSeconds(61);
    private static final LocalDateTime ARGUMENT_REVEAL = STARTED_AT.plusSeconds(63);
    private static final LocalDateTime REBUTTAL = STARTED_AT.plusSeconds(90);
    private static final LocalDateTime REBUTTAL_GRACE = STARTED_AT.plusSeconds(141);
    private static final LocalDateTime REBUTTAL_REVEAL = STARTED_AT.plusSeconds(143);
    private static final LocalDateTime CHAT = STARTED_AT.plusSeconds(150);

    private static Game newGame(int chatMaxLength, int maxChatsPerParticipant) {
        return new Game(1L, STARTED_AT, Map.of(ALICE, ALICE_PARTICIPANT, BOB, BOB_PARTICIPANT), null,
                chatMaxLength, maxChatsPerParticipant, 200, 250);
    }

    private static Game newGame() {
        return newGame(100, 200);
    }

    private static void assertError(Runnable action, SessionErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Nested
    @DisplayName("채팅")
    class Chat {

        @Test
        @DisplayName("seqNo 를 1 부터 매기고 보낸 참가자 id · 구간 · 수신 시각을 남긴다")
        void appends() {
            Game game = newGame();

            GameMessage first = game.appendChat(ALICE, "안녕", CHAT);
            GameMessage second = game.appendChat(BOB, "반가워", CHAT.plusSeconds(1));

            assertThat(first.seqNo()).isEqualTo(1);
            assertThat(first.participantId()).isEqualTo(ALICE_PARTICIPANT);
            assertThat(first.type()).isEqualTo(MessageType.CHAT);
            assertThat(first.phase()).isEqualTo(DebatePhase.CHAT);
            assertThat(first.receivedAt()).isEqualTo(CHAT);
            assertThat(second.seqNo()).isEqualTo(2);
            assertThat(second.participantId()).isEqualTo(BOB_PARTICIPANT);
        }

        @ParameterizedTest(name = "시작 후 {0}초")
        @ValueSource(longs = {143, 200, 230, 259})
        @DisplayName("반론이 공개된 143초부터 CHAT 이 끝날 때까지 받는다 (마지막 30초도 채팅)")
        void allowedPhases(long elapsedSeconds) {
            GameMessage message = newGame().appendChat(ALICE, "주장", STARTED_AT.plusSeconds(elapsedSeconds));

            assertThat(message.phase()).isEqualTo(DebatePhase.CHAT);
        }

        @ParameterizedTest(name = "시작 후 {0}초")
        @ValueSource(longs = {0, 59, 60, 79, 80, 139, 140, 142, 260, 3600})
        @DisplayName("PREP · REVEAL · REBUTTAL · 반론 공개 전 CHAT(140~143초) · JUDGING 에서는 INVALID_PHASE")
        void rejectedPhases(long elapsedSeconds) {
            Game game = newGame();

            assertError(() -> game.appendChat(ALICE, "주장", STARTED_AT.plusSeconds(elapsedSeconds)),
                    SessionErrorCode.INVALID_PHASE);
        }

        @Test
        @DisplayName("마감 시각에 도착한 메시지는 거부한다 (260.000초는 JUDGING)")
        void deadlineIsServerTime() {
            Game game = newGame();

            game.appendChat(ALICE, "마감 직전", STARTED_AT.plusSeconds(260).minusNanos(1_000_000));
            assertError(() -> game.appendChat(ALICE, "마감", STARTED_AT.plusSeconds(260)),
                    SessionErrorCode.INVALID_PHASE);
        }

        @Test
        @DisplayName("참가자가 아니면(관전자) 구간과 상관없이 NOT_PARTICIPANT")
        void spectatorCannotSend() {
            Game game = newGame();

            assertError(() -> game.appendChat(SPECTATOR, "관전자", CHAT), SessionErrorCode.NOT_PARTICIPANT);
            assertError(() -> game.appendChat(SPECTATOR, "관전자", STARTED_AT), SessionErrorCode.NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("글자 수 상한까지는 받고, 넘으면 CONTENT_TOO_LONG")
        void maxLength() {
            Game game = newGame();

            game.appendChat(ALICE, "가".repeat(100), CHAT);
            assertError(() -> game.appendChat(ALICE, "가".repeat(101), CHAT), SessionErrorCode.CONTENT_TOO_LONG);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", " ", "\n\t"})
        @DisplayName("비어 있거나 공백뿐이면 VALIDATION_FAILED 이고 seqNo 를 쓰지 않는다")
        void rejectsBlank(String blank) {
            Game game = newGame();

            assertThatThrownBy(() -> game.appendChat(ALICE, blank, CHAT))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.VALIDATION_FAILED);
            assertThat(game.appendChat(ALICE, "다음", CHAT).seqNo()).isEqualTo(1);
        }

        @Test
        @DisplayName("본문이 없으면(null) VALIDATION_FAILED")
        void rejectsNull() {
            Game game = newGame();

            assertThatThrownBy(() -> game.appendChat(ALICE, null, CHAT))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("이모지는 두 칸(char)을 써도 한 글자로 센다")
        void countsEmojiAsOneCharacter() {
            Game game = newGame(3, 200);
            String threeEmojis = "😀😀😀";   // String.length() 는 6

            game.appendChat(ALICE, threeEmojis, CHAT);
            assertError(() -> game.appendChat(ALICE, threeEmojis + "😀", CHAT), SessionErrorCode.CONTENT_TOO_LONG);
        }

        @Test
        @DisplayName("거부된 메시지는 seqNo 를 쓰지 않는다")
        void rejectedDoesNotConsumeSeq() {
            Game game = newGame();

            game.appendChat(ALICE, "1", CHAT);
            assertError(() -> game.appendChat(ALICE, "가".repeat(101), CHAT), SessionErrorCode.CONTENT_TOO_LONG);

            assertThat(game.appendChat(ALICE, "2", CHAT).seqNo()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("채팅 수 상한 (참가자별)")
    class ChatLimit {

        @Test
        @DisplayName("한 참가자가 상한에 닿으면 그 참가자만 MESSAGE_LIMIT_EXCEEDED")
        void perParticipant() {
            Game game = newGame(100, 2);

            game.appendChat(ALICE, "1", CHAT);
            game.appendChat(ALICE, "2", CHAT);

            assertError(() -> game.appendChat(ALICE, "3", CHAT), SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
            // 상대는 영향을 받지 않는다
            game.appendChat(BOB, "반박", CHAT);
            game.appendChat(BOB, "재반박", CHAT);
            assertError(() -> game.appendChat(BOB, "또", CHAT), SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("거부된 채팅은 개수에 들어가지 않는다")
        void rejectedNotCounted() {
            Game game = newGame(3, 1);

            assertError(() -> game.appendChat(ALICE, "너무 길다", CHAT), SessionErrorCode.CONTENT_TOO_LONG);
            game.appendChat(ALICE, "짧다", CHAT);
        }
    }

    @Nested
    @DisplayName("주장 · 반론 제출")
    class Memo {

        @Test
        @DisplayName("PREP 주장 · REBUTTAL 반론을 구간마다 하나씩 받고, 다시 제출하면 덮어쓴다")
        void savesAndOverwrites() {
            Game game = newGame();

            game.saveMemo(ALICE, "초안", PREP);
            GameMemo saved = game.saveMemo(ALICE, "고친 주장", PREP.plusSeconds(5));
            game.saveMemo(ALICE, "반론", REBUTTAL);

            assertThat(saved.phase()).isEqualTo(DebatePhase.PREP);
            assertThat(saved.submittedAt()).isEqualTo(PREP.plusSeconds(5));
            assertThat(game.memosOf(ALICE))
                    .extracting(GameMemo::phase, GameMemo::content)
                    .containsExactly(
                            tuple(DebatePhase.PREP, "고친 주장"),
                            tuple(DebatePhase.REBUTTAL, "반론"));
        }

        @Test
        @DisplayName("제출한 글은 공개 전에는 메시지에 남지 않고, 각자 자기 글만 본다")
        void privateUntilReveal() {
            Game game = newGame();

            game.saveMemo(ALICE, "앨리스 주장", PREP);

            assertThat(game.messagesAfter(0)).isEmpty();
            assertThat(game.memosOf(BOB)).isEmpty();
        }

        @ParameterizedTest(name = "시작 후 {0}ms")
        @ValueSource(longs = {60_000, 62_999, 140_000, 142_999})
        @DisplayName("작성 구간이 끝난 뒤 3초 유예 안의 제출(시간 종료 순간의 자동 제출)은 받는다")
        void acceptsWithinGrace(long elapsedMillis) {
            Game game = newGame();

            GameMemo memo = game.saveMemo(ALICE, "자동 제출", STARTED_AT.plusNanos(elapsedMillis * 1_000_000));

            assertThat(memo.phase()).isEqualTo(elapsedMillis < 100_000 ? DebatePhase.PREP : DebatePhase.REBUTTAL);
        }

        @ParameterizedTest(name = "시작 후 {0}초")
        @ValueSource(longs = {63, 79, 143, 200, 260})
        @DisplayName("유예가 끝난 뒤 · REVEAL · CHAT · JUDGING 에서는 INVALID_PHASE")
        void rejectsOutsideMemoPhases(long elapsedSeconds) {
            Game game = newGame();

            assertError(() -> game.saveMemo(ALICE, "주장", STARTED_AT.plusSeconds(elapsedSeconds)),
                    SessionErrorCode.INVALID_PHASE);
        }

        @Test
        @DisplayName("주장은 200자, 반론은 250자까지 받고 넘으면 CONTENT_TOO_LONG")
        void maxLength() {
            Game game = newGame();

            game.saveMemo(ALICE, "가".repeat(200), PREP);
            assertError(() -> game.saveMemo(ALICE, "가".repeat(201), PREP), SessionErrorCode.CONTENT_TOO_LONG);
            game.saveMemo(ALICE, "나".repeat(250), REBUTTAL);
            assertError(() -> game.saveMemo(ALICE, "나".repeat(251), REBUTTAL), SessionErrorCode.CONTENT_TOO_LONG);

            assertThat(game.memosOf(ALICE)).extracting(GameMemo::content)
                    .containsExactly("가".repeat(200), "나".repeat(250));
        }

        @Test
        @DisplayName("빈 문자열도 제출로 받고, 본문이 없으면(null) VALIDATION_FAILED")
        void blankAndNull() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);

            game.saveMemo(ALICE, "", PREP);

            assertThat(game.memosOf(ALICE)).singleElement().extracting(GameMemo::content).isEqualTo("");
            assertThatThrownBy(() -> game.saveMemo(ALICE, null, PREP))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("관전자는 제출 · 조회 모두 NOT_PARTICIPANT")
        void spectatorCannotWriteOrRead() {
            Game game = newGame();

            assertError(() -> game.saveMemo(SPECTATOR, "주장", PREP), SessionErrorCode.NOT_PARTICIPANT);
            assertError(() -> game.memosOf(SPECTATOR), SessionErrorCode.NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("끝난 게임은 SESSION_NOT_IN_PROGRESS")
        void finished() {
            Game game = newGame();
            game.finish();

            assertError(() -> game.saveMemo(ALICE, "주장", PREP), SessionErrorCode.SESSION_NOT_IN_PROGRESS);
            assertError(() -> game.memosOf(ALICE), SessionErrorCode.SESSION_NOT_IN_PROGRESS);
        }

        @Test
        @DisplayName("toString 에 본문을 넣지 않는다 (로그 유출 방지)")
        void toStringHidesContent() {
            GameMemo memo = newGame().saveMemo(ALICE, "비밀스러운 주장", PREP);

            assertThat(memo.toString()).doesNotContain("비밀스러운 주장").contains("length=8");
        }
    }

    @Nested
    @DisplayName("제출 여부")
    class Submitted {

        @Test
        @DisplayName("지금 작성 구간에 제출한 참가자 id 만 준다 (유예 포함)")
        void submittedInCurrentPhase() {
            Game game = newGame();

            assertThat(game.submittedParticipantIds(PREP)).isEmpty();
            game.saveMemo(ALICE, "주장", PREP);

            assertThat(game.submittedParticipantIds(PREP)).containsExactly(ALICE_PARTICIPANT);
            assertThat(game.submittedParticipantIds(PREP_GRACE)).containsExactly(ALICE_PARTICIPANT);
            // 반론 구간은 새로 센다
            assertThat(game.submittedParticipantIds(REBUTTAL)).isEmpty();
        }

        @Test
        @DisplayName("빈 글을 제출해도 제출한 것으로 본다")
        void blankCounts() {
            Game game = newGame();
            game.saveMemo(BOB, "", PREP);

            assertThat(game.submittedParticipantIds(PREP)).containsExactly(BOB_PARTICIPANT);
        }

        @ParameterizedTest(name = "시작 후 {0}초")
        @ValueSource(longs = {63, 79, 143, 260})
        @DisplayName("작성 구간(유예 포함)이 아니면 null")
        void nullOutsideMemoPhases(long elapsedSeconds) {
            assertThat(newGame().submittedParticipantIds(STARTED_AT.plusSeconds(elapsedSeconds))).isNull();
        }
    }

    @Nested
    @DisplayName("주장 · 반론 공개")
    class Argument {

        @Test
        @DisplayName("63초가 되면 양쪽 주장을 참가자 id 순서로 ARGUMENT(PREP) 로 공개한다")
        void revealsArgumentsAfterGrace() {
            Game game = newGame();
            game.saveMemo(BOB, "밥 주장", PREP);
            game.saveMemo(ALICE, "앨리스 주장", PREP);

            List<GameMessage> revealed = game.revealArguments(ARGUMENT_REVEAL);

            assertThat(revealed)
                    .extracting(GameMessage::seqNo, GameMessage::participantId, GameMessage::type,
                            GameMessage::phase, GameMessage::content)
                    .containsExactly(
                            tuple(1L, ALICE_PARTICIPANT, MessageType.ARGUMENT, DebatePhase.PREP, "앨리스 주장"),
                            tuple(2L, BOB_PARTICIPANT, MessageType.ARGUMENT, DebatePhase.PREP, "밥 주장"));
            assertThat(revealed).allSatisfy(message -> assertThat(message.receivedAt()).isEqualTo(ARGUMENT_REVEAL));
            assertThat(game.messagesAfter(0)).isEqualTo(revealed);
        }

        @Test
        @DisplayName("유예 안(60~63초)에는 공개하지 않고, 유예 안에 들어온 자동 제출이 공개된다")
        void graceSubmissionIsRevealed() {
            Game game = newGame();
            game.saveMemo(ALICE, "초안", PREP);

            assertThat(game.revealArguments(PREP_GRACE)).isEmpty();
            game.saveMemo(ALICE, "자동 제출본", PREP_GRACE);

            assertThat(game.revealArguments(ARGUMENT_REVEAL)).extracting(GameMessage::content)
                    .containsExactly("자동 제출본");
        }

        @Test
        @DisplayName("공개된 구간의 글은 그보다 이른 시각으로도 고칠 수 없다 (락을 기다리는 사이 공개된 경우)")
        void cannotEditRevealedMemo() {
            Game game = newGame();
            game.saveMemo(ALICE, "공개될 주장", PREP);
            game.revealArguments(ARGUMENT_REVEAL);

            assertError(() -> game.saveMemo(ALICE, "몰래 고친 주장", PREP_GRACE), SessionErrorCode.INVALID_PHASE);

            assertThat(game.memosOf(ALICE)).extracting(GameMemo::content).containsExactly("공개될 주장");
            assertThat(game.messagesAfter(0)).extracting(GameMessage::content).containsExactly("공개될 주장");
        }

        @Test
        @DisplayName("주장이 공개돼도 반론은 REBUTTAL 구간에 제출할 수 있다")
        void canWriteRebuttalAfterArgumentRevealed() {
            Game game = newGame();
            game.saveMemo(ALICE, "첫 주장", PREP);
            game.revealArguments(ARGUMENT_REVEAL);

            assertThat(game.saveMemo(ALICE, "반론", REBUTTAL).phase()).isEqualTo(DebatePhase.REBUTTAL);
        }

        @Test
        @DisplayName("구간마다 한 번만 공개한다")
        void revealsOnce() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);

            game.revealArguments(ARGUMENT_REVEAL);

            assertThat(game.revealArguments(ARGUMENT_REVEAL.plusSeconds(10))).isEmpty();
            assertThat(game.messagesAfter(0)).hasSize(1);
        }

        @Test
        @DisplayName("공개 시각 전에는 공개하지 않는다")
        void notBeforeRevealTime() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);
            game.saveMemo(ALICE, "반론", REBUTTAL);

            assertThat(game.revealArguments(PREP.plusSeconds(1))).isEmpty();
            game.revealArguments(ARGUMENT_REVEAL);
            assertThat(game.revealArguments(REBUTTAL_GRACE)).isEmpty();
            assertThat(game.messagesAfter(0)).extracting(GameMessage::content).containsExactly("주장");
        }

        @Test
        @DisplayName("143초가 되면 반론을 ARGUMENT(REBUTTAL) 로 공개한다")
        void revealsRebuttalAfterGrace() {
            Game game = newGame();
            game.saveMemo(ALICE, "반론", REBUTTAL);

            assertThat(game.revealArguments(REBUTTAL_REVEAL))
                    .singleElement()
                    .satisfies(message -> {
                        assertThat(message.phase()).isEqualTo(DebatePhase.REBUTTAL);
                        assertThat(message.content()).isEqualTo("반론");
                    });
        }

        @Test
        @DisplayName("늦게 불려도 공개하지 못한 앞 구간 글부터 차례로 공개한다")
        void catchesUpInOrder() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);
            game.saveMemo(ALICE, "반론", REBUTTAL);

            List<GameMessage> revealed = game.revealArguments(CHAT);

            assertThat(revealed).extracting(GameMessage::phase, GameMessage::content)
                    .containsExactly(
                            tuple(DebatePhase.PREP, "주장"),
                            tuple(DebatePhase.REBUTTAL, "반론"));
        }

        @Test
        @DisplayName("비었거나 공백뿐인 글, 제출하지 않은 참가자는 공개하지 않는다")
        void skipsBlank() {
            Game game = newGame();
            game.saveMemo(ALICE, "   ", PREP);

            assertThat(game.revealArguments(ARGUMENT_REVEAL)).isEmpty();
        }

        @Test
        @DisplayName("첫 채팅이 공개보다 먼저 와도 반론이 앞 seqNo 를 받는다")
        void revealedBeforeFirstChat() {
            Game game = newGame();
            game.saveMemo(ALICE, "반론", REBUTTAL);
            game.saveMemo(BOB, "반대 반론", REBUTTAL);

            GameMessage chat = game.appendChat(BOB, "첫 채팅", REBUTTAL_REVEAL);

            assertThat(chat.seqNo()).isEqualTo(3);
            assertThat(game.messagesAfter(0)).extracting(GameMessage::type)
                    .containsExactly(MessageType.ARGUMENT, MessageType.ARGUMENT, MessageType.CHAT);
            assertThat(game.revealArguments(REBUTTAL_REVEAL)).isEmpty();
        }

        @Test
        @DisplayName("채팅이 거부돼도 공개는 남는다")
        void revealedEvenIfChatRejected() {
            Game game = newGame();
            game.saveMemo(ALICE, "반론", REBUTTAL);

            assertError(() -> game.appendChat(SPECTATOR, "관전자", CHAT), SessionErrorCode.NOT_PARTICIPANT);
            assertError(() -> game.appendChat(ALICE, "가".repeat(101), CHAT), SessionErrorCode.CONTENT_TOO_LONG);

            assertThat(game.messagesAfter(0)).extracting(GameMessage::type).containsExactly(MessageType.ARGUMENT);
        }

        @Test
        @DisplayName("공개된 글은 참가자별 채팅 수 상한에 세지 않는다")
        void notCountedInChatLimit() {
            Game game = newGame(100, 1);
            game.saveMemo(ALICE, "주장", PREP);
            game.saveMemo(ALICE, "반론", REBUTTAL);

            game.appendChat(ALICE, "채팅", CHAT);

            assertError(() -> game.appendChat(ALICE, "두 번째", CHAT), SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
            assertThat(game.messagesAfter(0)).hasSize(3);
        }

        @Test
        @DisplayName("끝난 게임은 공개하지 않는다")
        void notAfterFinish() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);
            game.finish();

            assertThat(game.revealArguments(ARGUMENT_REVEAL)).isEmpty();
            assertThat(game.messagesAfter(0)).isEmpty();
        }
    }

    @Test
    @DisplayName("끝난 게임은 SESSION_NOT_IN_PROGRESS. 받은 메시지는 계속 조회된다")
    void finished() {
        Game game = newGame();
        game.appendChat(ALICE, "1", CHAT);

        game.finish();

        assertThat(game.isFinished()).isTrue();
        assertError(() -> game.appendChat(ALICE, "2", CHAT), SessionErrorCode.SESSION_NOT_IN_PROGRESS);
        assertThat(game.messagesAfter(0)).hasSize(1);
    }

    @Test
    @DisplayName("messagesAfter 는 seqNo 가 afterSeq 보다 큰 메시지를 오름차순으로 준다")
    void messagesAfter() {
        Game game = newGame();
        for (int i = 0; i < 5; i++) {
            game.appendChat(ALICE, "m" + i, CHAT);
        }

        assertThat(game.messagesAfter(0)).extracting(GameMessage::seqNo).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(game.messagesAfter(3)).extracting(GameMessage::seqNo).containsExactly(4L, 5L);
        assertThat(game.messagesAfter(5)).isEmpty();
        assertThat(game.messagesAfter(100)).isEmpty();
        assertThat(game.messagesAfter(-1)).hasSize(5);
    }

    @Test
    @DisplayName("messagesAfter 결과는 복사본이라 이후 메시지가 추가돼도 바뀌지 않는다")
    void messagesAfterIsSnapshot() {
        Game game = newGame();
        game.appendChat(ALICE, "1", CHAT);

        List<GameMessage> snapshot = game.messagesAfter(0);
        game.appendChat(ALICE, "2", CHAT);

        assertThat(snapshot).hasSize(1);
    }

    @Test
    @DisplayName("참가자 id 는 사용자 id 로 찾고, 관전자는 null")
    void participantLookup() {
        Game game = newGame();

        assertThat(game.participantIdOf(ALICE)).isEqualTo(ALICE_PARTICIPANT);
        assertThat(game.isParticipant(BOB)).isTrue();
        assertThat(game.participantIdOf(SPECTATOR)).isNull();
        assertThat(game.isParticipant(SPECTATOR)).isFalse();
    }

    @Test
    @DisplayName("두 참가자가 동시에 보내도 seqNo 가 겹치거나 빠지지 않는다")
    void concurrentAppends() throws Exception {
        Game game = newGame();
        int perUser = 200;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (long user : new long[]{ALICE, BOB}) {
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perUser; i++) {
                        game.appendChat(user, "m", CHAT);
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        List<GameMessage> messages = game.messagesAfter(0);
        assertThat(messages).hasSize(perUser * 2);
        for (int i = 0; i < messages.size(); i++) {
            assertThat(messages.get(i).seqNo()).isEqualTo(i + 1L);
        }
    }

    @Test
    @DisplayName("toString 에 채팅 본문을 넣지 않는다 (로그 유출 방지)")
    void toStringHidesContent() {
        GameMessage message = newGame().appendChat(ALICE, "비밀스러운 주장", CHAT);

        assertThat(message.toString()).doesNotContain("비밀스러운 주장").contains("length=8");
    }
}
