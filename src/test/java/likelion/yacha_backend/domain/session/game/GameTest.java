package likelion.yacha_backend.domain.session.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private static final LocalDateTime FINAL = STARTED_AT.plusSeconds(450);

    private static Game newGame(int chatMaxLength, int maxMessages) {
        return new Game(1L, STARTED_AT, Map.of(ALICE, ALICE_PARTICIPANT, BOB, BOB_PARTICIPANT),
                chatMaxLength, maxMessages);
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
        @ValueSource(longs = {60, 239, 270, 449})
        @DisplayName("CHAT_1 · CHAT_2 구간에서 받는다")
        void allowedPhases(long elapsedSeconds) {
            GameMessage message = newGame().appendChat(ALICE, "주장", STARTED_AT.plusSeconds(elapsedSeconds));

            assertThat(message.phase().isChatAllowed()).isTrue();
        }

        @ParameterizedTest(name = "시작 후 {0}초")
        @ValueSource(longs = {0, 59, 240, 269, 450, 479, 480})
        @DisplayName("PREP · REBUTTAL · FINAL · JUDGING 에서는 INVALID_PHASE")
        void rejectedPhases(long elapsedSeconds) {
            Game game = newGame();

            assertError(() -> game.appendChat(ALICE, "주장", STARTED_AT.plusSeconds(elapsedSeconds)),
                    SessionErrorCode.INVALID_PHASE);
        }

        @Test
        @DisplayName("마감 시각에 도착한 메시지는 거부한다 (240.000초는 REBUTTAL)")
        void deadlineIsServerTime() {
            Game game = newGame();

            game.appendChat(ALICE, "마감 직전", STARTED_AT.plusSeconds(240).minusNanos(1_000_000));
            assertError(() -> game.appendChat(ALICE, "마감", STARTED_AT.plusSeconds(240)),
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
        @ValueSource(longs = {60, 449, 480})
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

    @Test
    @DisplayName("메시지 수 상한에 닿으면 MESSAGE_LIMIT_EXCEEDED (최종변론 포함)")
    void messageLimit() {
        Game game = newGame(300, 2);

        game.appendChat(ALICE, "1", CHAT_1);
        game.appendChat(BOB, "2", CHAT_1);

        assertError(() -> game.appendChat(ALICE, "3", CHAT_1), SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
        assertError(() -> game.submitFinal(ALICE, "결론", FINAL), SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
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
