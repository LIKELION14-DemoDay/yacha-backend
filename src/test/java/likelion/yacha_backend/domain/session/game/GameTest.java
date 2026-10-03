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

    private static final LocalDateTime CHAT_1 = STARTED_AT.plusSeconds(60);
    private static final LocalDateTime FINAL = STARTED_AT.plusSeconds(330);
    private static final LocalDateTime PREP = STARTED_AT.plusSeconds(10);
    private static final LocalDateTime REBUTTAL = STARTED_AT.plusSeconds(160);
    private static final LocalDateTime CHAT_2 = STARTED_AT.plusSeconds(270);

    private static Game newGame(int chatMaxLength, int maxChatsPerParticipant) {
        return new Game(1L, STARTED_AT, Map.of(ALICE, ALICE_PARTICIPANT, BOB, BOB_PARTICIPANT),
                chatMaxLength, maxChatsPerParticipant, 300);
    }

    private static Game newGame() {
        return newGame(300, 500);
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

            GameMessage first = game.appendChat(ALICE, "안녕", CHAT_1);
            GameMessage second = game.appendChat(BOB, "반가워", CHAT_1.plusSeconds(1));

            assertThat(first.seqNo()).isEqualTo(1);
            assertThat(first.participantId()).isEqualTo(ALICE_PARTICIPANT);
            assertThat(first.type()).isEqualTo(MessageType.CHAT);
            assertThat(first.phase()).isEqualTo(DebatePhase.CHAT_1);
            assertThat(first.receivedAt()).isEqualTo(CHAT_1);
            assertThat(second.seqNo()).isEqualTo(2);
            assertThat(second.participantId()).isEqualTo(BOB_PARTICIPANT);
        }

        @ParameterizedTest(name = "시작 후 {0}초")
        @ValueSource(longs = {60, 149, 270, 329})
        @DisplayName("CHAT_1 · CHAT_2 구간에서 받는다")
        void allowedPhases(long elapsedSeconds) {
            GameMessage message = newGame().appendChat(ALICE, "주장", STARTED_AT.plusSeconds(elapsedSeconds));

            assertThat(message.phase().isChatAllowed()).isTrue();
        }

        @ParameterizedTest(name = "시작 후 {0}초")
        @ValueSource(longs = {0, 59, 150, 269, 330, 359, 360})
        @DisplayName("PREP · REBUTTAL · FINAL · JUDGING 에서는 INVALID_PHASE")
        void rejectedPhases(long elapsedSeconds) {
            Game game = newGame();

            assertError(() -> game.appendChat(ALICE, "주장", STARTED_AT.plusSeconds(elapsedSeconds)),
                    SessionErrorCode.INVALID_PHASE);
        }

        @Test
        @DisplayName("마감 시각에 도착한 메시지는 거부한다 (150.000초는 REBUTTAL)")
        void deadlineIsServerTime() {
            Game game = newGame();

            game.appendChat(ALICE, "마감 직전", STARTED_AT.plusSeconds(150).minusNanos(1_000_000));
            assertError(() -> game.appendChat(ALICE, "마감", STARTED_AT.plusSeconds(150)),
                    SessionErrorCode.INVALID_PHASE);
        }

        @Test
        @DisplayName("참가자가 아니면(관전자) 구간과 상관없이 NOT_PARTICIPANT")
        void spectatorCannotSend() {
            Game game = newGame();

            assertError(() -> game.appendChat(SPECTATOR, "관전자", CHAT_1), SessionErrorCode.NOT_PARTICIPANT);
            assertError(() -> game.appendChat(SPECTATOR, "관전자", STARTED_AT), SessionErrorCode.NOT_PARTICIPANT);
        }

        @Test
        @DisplayName("글자 수 상한까지는 받고, 넘으면 CONTENT_TOO_LONG")
        void maxLength() {
            Game game = newGame();

            game.appendChat(ALICE, "가".repeat(300), CHAT_1);
            assertError(() -> game.appendChat(ALICE, "가".repeat(301), CHAT_1), SessionErrorCode.CONTENT_TOO_LONG);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", " ", "\n\t"})
        @DisplayName("비어 있거나 공백뿐이면 VALIDATION_FAILED 이고 seqNo 를 쓰지 않는다")
        void rejectsBlank(String blank) {
            Game game = newGame();

            assertThatThrownBy(() -> game.appendChat(ALICE, blank, CHAT_1))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.VALIDATION_FAILED);
            assertThat(game.appendChat(ALICE, "다음", CHAT_1).seqNo()).isEqualTo(1);
        }

        @Test
        @DisplayName("본문이 없으면(null) VALIDATION_FAILED")
        void rejectsNull() {
            Game game = newGame();

            assertThatThrownBy(() -> game.appendChat(ALICE, null, CHAT_1))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(GlobalErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("이모지는 두 칸(char)을 써도 한 글자로 센다")
        void countsEmojiAsOneCharacter() {
            Game game = newGame(3, 500);
            String threeEmojis = "😀😀😀";   // String.length() 는 6

            game.appendChat(ALICE, threeEmojis, CHAT_1);
            assertError(() -> game.appendChat(ALICE, threeEmojis + "😀", CHAT_1), SessionErrorCode.CONTENT_TOO_LONG);
        }

        @Test
        @DisplayName("거부된 메시지는 seqNo 를 쓰지 않는다")
        void rejectedDoesNotConsumeSeq() {
            Game game = newGame();

            game.appendChat(ALICE, "1", CHAT_1);
            assertError(() -> game.appendChat(ALICE, "가".repeat(301), CHAT_1), SessionErrorCode.CONTENT_TOO_LONG);

            assertThat(game.appendChat(ALICE, "2", CHAT_1).seqNo()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("최종변론")
    class Final {

        @Test
        @DisplayName("FINAL 구간에서 참가자당 1건 받는다")
        void submitsOncePerParticipant() {
            Game game = newGame();

            GameMessage alice = game.submitFinal(ALICE, "결론", FINAL);
            GameMessage bob = game.submitFinal(BOB, "결론", FINAL.plusSeconds(1));

            assertThat(alice.type()).isEqualTo(MessageType.FINAL);
            assertThat(alice.phase()).isEqualTo(DebatePhase.FINAL);
            assertThat(bob.seqNo()).isEqualTo(alice.seqNo() + 1);
            assertError(() -> game.submitFinal(ALICE, "한 번 더", FINAL.plusSeconds(2)),
                    SessionErrorCode.FINAL_ALREADY_SUBMITTED);
        }

        @ParameterizedTest(name = "시작 후 {0}초")
        @ValueSource(longs = {60, 329, 360})
        @DisplayName("FINAL 이 아닌 구간에서는 INVALID_PHASE")
        void onlyInFinal(long elapsedSeconds) {
            Game game = newGame();

            assertError(() -> game.submitFinal(ALICE, "결론", STARTED_AT.plusSeconds(elapsedSeconds)),
                    SessionErrorCode.INVALID_PHASE);
        }

        @Test
        @DisplayName("FINAL 에는 채팅을 받지 않는다")
        void noChatInFinal() {
            assertError(() -> newGame().appendChat(ALICE, "채팅", FINAL), SessionErrorCode.INVALID_PHASE);
        }

        @Test
        @DisplayName("100자까지 받고 넘으면 CONTENT_TOO_LONG. 거부되면 다시 낼 수 있다")
        void maxLength() {
            Game game = newGame();

            assertError(() -> game.submitFinal(ALICE, "가".repeat(101), FINAL), SessionErrorCode.CONTENT_TOO_LONG);
            game.submitFinal(ALICE, "가".repeat(100), FINAL);
        }

        @Test
        @DisplayName("관전자는 NOT_PARTICIPANT")
        void spectatorCannotSubmit() {
            assertError(() -> newGame().submitFinal(SPECTATOR, "결론", FINAL), SessionErrorCode.NOT_PARTICIPANT);
        }
    }

    @Nested
    @DisplayName("채팅 수 상한 (참가자별)")
    class ChatLimit {

        @Test
        @DisplayName("한 참가자가 상한에 닿으면 그 참가자만 MESSAGE_LIMIT_EXCEEDED")
        void perParticipant() {
            Game game = newGame(300, 2);

            game.appendChat(ALICE, "1", CHAT_1);
            game.appendChat(ALICE, "2", CHAT_1);

            assertError(() -> game.appendChat(ALICE, "3", CHAT_1), SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
            // 상대는 영향을 받지 않는다
            game.appendChat(BOB, "반박", CHAT_1);
            game.appendChat(BOB, "재반박", CHAT_1);
            assertError(() -> game.appendChat(BOB, "또", CHAT_1), SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("채팅 상한에 닿아도 최종변론은 낼 수 있다")
        void finalNotCounted() {
            Game game = newGame(300, 1);
            game.appendChat(ALICE, "1", CHAT_1);
            game.appendChat(BOB, "2", CHAT_1);

            game.submitFinal(ALICE, "결론", FINAL);
            game.submitFinal(BOB, "결론", FINAL);

            assertThat(game.messagesAfter(0)).hasSize(4);
        }

        @Test
        @DisplayName("거부된 채팅은 개수에 들어가지 않는다")
        void rejectedNotCounted() {
            Game game = newGame(3, 1);

            assertError(() -> game.appendChat(ALICE, "너무 길다", CHAT_1), SessionErrorCode.CONTENT_TOO_LONG);
            game.appendChat(ALICE, "짧다", CHAT_1);
        }
    }

    @Nested
    @DisplayName("주장 작성")
    class Memo {

        @Test
        @DisplayName("PREP · REBUTTAL 에서 구간마다 하나씩 저장하고, 다시 저장하면 덮어쓴다")
        void savesAndOverwrites() {
            Game game = newGame();

            game.saveMemo(ALICE, "초안", PREP);
            GameMemo saved = game.saveMemo(ALICE, "고친 주장", PREP.plusSeconds(5));
            game.saveMemo(ALICE, "반박", REBUTTAL);

            assertThat(saved.phase()).isEqualTo(DebatePhase.PREP);
            assertThat(saved.updatedAt()).isEqualTo(PREP.plusSeconds(5));
            assertThat(game.memosOf(ALICE))
                    .extracting(GameMemo::phase, GameMemo::content)
                    .containsExactly(
                            tuple(DebatePhase.PREP, "고친 주장"),
                            tuple(DebatePhase.REBUTTAL, "반박"));
        }

        @Test
        @DisplayName("작성 중인 주장은 메시지에 남지 않고, 각자 자기 주장만 본다")
        void privateWhileWriting() {
            Game game = newGame();

            game.saveMemo(ALICE, "앨리스 주장", PREP);

            assertThat(game.messagesAfter(0)).isEmpty();
            assertThat(game.memosOf(BOB)).isEmpty();
        }

        @ParameterizedTest(name = "시작 후 {0}초")
        @ValueSource(longs = {60, 149, 270, 330, 360})
        @DisplayName("PREP · REBUTTAL 이 아니면 INVALID_PHASE (구간이 끝나면 고칠 수 없다)")
        void onlyInMemoPhases(long elapsedSeconds) {
            Game game = newGame();

            assertError(() -> game.saveMemo(ALICE, "주장", STARTED_AT.plusSeconds(elapsedSeconds)),
                    SessionErrorCode.INVALID_PHASE);
        }

        @Test
        @DisplayName("300자까지 받고 넘으면 CONTENT_TOO_LONG")
        void maxLength() {
            Game game = newGame();

            game.saveMemo(ALICE, "가".repeat(300), PREP);
            assertError(() -> game.saveMemo(ALICE, "가".repeat(301), PREP), SessionErrorCode.CONTENT_TOO_LONG);
            assertThat(game.memosOf(ALICE)).singleElement().extracting(GameMemo::content).isEqualTo("가".repeat(300));
        }

        @Test
        @DisplayName("빈 문자열로 비울 수 있고, 본문이 없으면(null) VALIDATION_FAILED")
        void clearAndNull() {
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
        @DisplayName("관전자는 저장 · 조회 모두 NOT_PARTICIPANT")
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
        @DisplayName("toString 에 주장 본문을 넣지 않는다 (로그 유출 방지)")
        void toStringHidesContent() {
            GameMemo memo = newGame().saveMemo(ALICE, "비밀스러운 주장", PREP);

            assertThat(memo.toString()).doesNotContain("비밀스러운 주장").contains("length=8");
        }
    }

    @Nested
    @DisplayName("주장 공개")
    class Argument {

        @Test
        @DisplayName("CHAT_1 이 되면 양쪽 PREP 주장을 참가자 id 순서로 ARGUMENT 로 공개한다")
        void revealsPrepAtChat1() {
            Game game = newGame();
            game.saveMemo(BOB, "밥 주장", PREP);
            game.saveMemo(ALICE, "앨리스 주장", PREP);

            List<GameMessage> revealed = game.revealArguments(CHAT_1);

            assertThat(revealed)
                    .extracting(GameMessage::seqNo, GameMessage::participantId, GameMessage::type,
                            GameMessage::phase, GameMessage::content)
                    .containsExactly(
                            tuple(1L, ALICE_PARTICIPANT, MessageType.ARGUMENT,
                                    DebatePhase.CHAT_1, "앨리스 주장"),
                            tuple(2L, BOB_PARTICIPANT, MessageType.ARGUMENT,
                                    DebatePhase.CHAT_1, "밥 주장"));
            assertThat(revealed).allSatisfy(message -> assertThat(message.receivedAt()).isEqualTo(CHAT_1));
            assertThat(game.messagesAfter(0)).isEqualTo(revealed);
        }

        @Test
        @DisplayName("공개된 구간의 주장은 그보다 이른 시각으로도 고칠 수 없다 (락을 기다리는 사이 공개된 경우)")
        void cannotEditRevealedMemo() {
            Game game = newGame();
            game.saveMemo(ALICE, "공개될 주장", PREP);
            game.revealArguments(CHAT_1);

            assertError(() -> game.saveMemo(ALICE, "몰래 고친 주장", PREP), SessionErrorCode.INVALID_PHASE);

            assertThat(game.memosOf(ALICE)).extracting(GameMemo::content).containsExactly("공개될 주장");
            assertThat(game.messagesAfter(0)).extracting(GameMessage::content).containsExactly("공개될 주장");
        }

        @Test
        @DisplayName("PREP 이 공개돼도 REBUTTAL 주장은 REBUTTAL 구간에 쓸 수 있다")
        void canWriteRebuttalAfterPrepRevealed() {
            Game game = newGame();
            game.saveMemo(ALICE, "첫 주장", PREP);
            game.revealArguments(CHAT_1);

            assertThat(game.saveMemo(ALICE, "반박", REBUTTAL).phase()).isEqualTo(DebatePhase.REBUTTAL);
        }

        @Test
        @DisplayName("구간마다 한 번만 공개한다")
        void revealsOnce() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);

            game.revealArguments(CHAT_1);

            assertThat(game.revealArguments(CHAT_1.plusSeconds(10))).isEmpty();
            assertThat(game.messagesAfter(0)).hasSize(1);
        }

        @Test
        @DisplayName("아직 작성 구간이면 공개하지 않는다")
        void notBeforeChatPhase() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);
            game.saveMemo(ALICE, "반박", REBUTTAL);

            assertThat(game.revealArguments(PREP.plusSeconds(1))).isEmpty();
            game.revealArguments(CHAT_1);
            assertThat(game.revealArguments(REBUTTAL.plusSeconds(1))).isEmpty();
            assertThat(game.messagesAfter(0)).extracting(GameMessage::content).containsExactly("주장");
        }

        @Test
        @DisplayName("CHAT_2 가 되면 REBUTTAL 주장을 CHAT_2 구간으로 공개한다")
        void revealsRebuttalAtChat2() {
            Game game = newGame();
            game.saveMemo(ALICE, "반박", REBUTTAL);

            assertThat(game.revealArguments(CHAT_2))
                    .singleElement()
                    .satisfies(message -> {
                        assertThat(message.phase()).isEqualTo(DebatePhase.CHAT_2);
                        assertThat(message.content()).isEqualTo("반박");
                    });
        }

        @Test
        @DisplayName("늦게 불려도 공개하지 못한 앞 구간 주장부터 차례로 공개한다")
        void catchesUpInOrder() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);
            game.saveMemo(ALICE, "반박", REBUTTAL);

            List<GameMessage> revealed = game.revealArguments(CHAT_2);

            assertThat(revealed).extracting(GameMessage::phase, GameMessage::content)
                    .containsExactly(
                            tuple(DebatePhase.CHAT_1, "주장"),
                            tuple(DebatePhase.CHAT_2, "반박"));
        }

        @Test
        @DisplayName("비었거나 공백뿐인 주장, 작성하지 않은 참가자는 공개하지 않는다")
        void skipsBlank() {
            Game game = newGame();
            game.saveMemo(ALICE, "   ", PREP);

            assertThat(game.revealArguments(CHAT_1)).isEmpty();
        }

        @Test
        @DisplayName("구간 첫 채팅이 공개보다 먼저 와도 주장이 앞 seqNo 를 받는다")
        void revealedBeforeFirstChat() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);
            game.saveMemo(BOB, "반대 주장", PREP);

            GameMessage chat = game.appendChat(BOB, "첫 채팅", CHAT_1);

            assertThat(chat.seqNo()).isEqualTo(3);
            assertThat(game.messagesAfter(0)).extracting(GameMessage::type)
                    .containsExactly(MessageType.ARGUMENT, MessageType.ARGUMENT, MessageType.CHAT);
            assertThat(game.revealArguments(CHAT_1)).isEmpty();
        }

        @Test
        @DisplayName("채팅이 거부돼도 주장 공개는 남는다")
        void revealedEvenIfChatRejected() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);

            assertError(() -> game.appendChat(SPECTATOR, "관전자", CHAT_1), SessionErrorCode.NOT_PARTICIPANT);
            assertError(() -> game.appendChat(ALICE, "가".repeat(301), CHAT_1), SessionErrorCode.CONTENT_TOO_LONG);

            assertThat(game.messagesAfter(0)).extracting(GameMessage::type).containsExactly(MessageType.ARGUMENT);
        }

        @Test
        @DisplayName("최종변론보다 공개하지 못한 주장이 먼저 기록된다")
        void revealedBeforeFinal() {
            Game game = newGame();
            game.saveMemo(ALICE, "반박", REBUTTAL);

            game.submitFinal(ALICE, "결론", FINAL);

            assertThat(game.messagesAfter(0)).extracting(GameMessage::type)
                    .containsExactly(MessageType.ARGUMENT, MessageType.FINAL);
        }

        @Test
        @DisplayName("공개된 주장은 참가자별 채팅 수 상한에 세지 않는다")
        void notCountedInChatLimit() {
            Game game = newGame(300, 1);
            game.saveMemo(ALICE, "주장", PREP);

            game.appendChat(ALICE, "채팅", CHAT_1);

            assertError(() -> game.appendChat(ALICE, "두 번째", CHAT_1), SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
            assertThat(game.messagesAfter(0)).hasSize(2);
        }

        @Test
        @DisplayName("끝난 게임은 공개하지 않는다")
        void notAfterFinish() {
            Game game = newGame();
            game.saveMemo(ALICE, "주장", PREP);
            game.finish();

            assertThat(game.revealArguments(CHAT_1)).isEmpty();
            assertThat(game.messagesAfter(0)).isEmpty();
        }
    }

    @Test
    @DisplayName("끝난 게임은 SESSION_NOT_IN_PROGRESS. 받은 메시지는 계속 조회된다")
    void finished() {
        Game game = newGame();
        game.appendChat(ALICE, "1", CHAT_1);

        game.finish();

        assertThat(game.isFinished()).isTrue();
        assertError(() -> game.appendChat(ALICE, "2", CHAT_1), SessionErrorCode.SESSION_NOT_IN_PROGRESS);
        assertError(() -> game.submitFinal(ALICE, "결론", FINAL), SessionErrorCode.SESSION_NOT_IN_PROGRESS);
        assertThat(game.messagesAfter(0)).hasSize(1);
    }

    @Test
    @DisplayName("messagesAfter 는 seqNo 가 afterSeq 보다 큰 메시지를 오름차순으로 준다")
    void messagesAfter() {
        Game game = newGame();
        for (int i = 0; i < 5; i++) {
            game.appendChat(ALICE, "m" + i, CHAT_1);
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
        game.appendChat(ALICE, "1", CHAT_1);

        List<GameMessage> snapshot = game.messagesAfter(0);
        game.appendChat(ALICE, "2", CHAT_1);

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
                        game.appendChat(user, "m", CHAT_1);
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
        GameMessage message = newGame().appendChat(ALICE, "비밀스러운 주장", CHAT_1);

        assertThat(message.toString()).doesNotContain("비밀스러운 주장").contains("length=8");
    }
}
