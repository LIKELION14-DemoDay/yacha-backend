package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import likelion.yacha_backend.domain.ai.JudgeCriterion;
import likelion.yacha_backend.domain.ai.JudgeRequest;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.heart.repository.HeartUseRepository;
import likelion.yacha_backend.domain.heart.service.HeartService;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.JudgeStatus;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import likelion.yacha_backend.global.security.jwt.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 하트 돌려주기 — 무효(ABORTED)와 판정 최종 실패. 몰수패 · 판정 성공은 돌려주지 않습니다.
 * 실제 판정 실행기 · 타이머로 확인합니다 ({@link TimerTestSupport}, 결과 보관 시간 2초, 재판정 2번).
 *
 * <p>방은 {@link SessionFixture} 로 만들고, 매칭 트랜잭션처럼 {@link HeartService#consume} 으로 하트를 씁니다.
 */
@DisplayName("하트 돌려주기 — 무효 · 판정 실패만, 한 번만")
class HeartRefundTest extends TimerTestSupport {

    private static final long WAIT_MILLIS = 8_000;
    private static final long IN_CHAT = 150;

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private HeartService heartService;

    @Autowired
    private HeartUseRepository heartUseRepository;

    @Autowired
    private GameExitService gameExitService;

    @Autowired
    private InProgressSessionCleaner inProgressSessionCleaner;

    @Autowired
    private JudgeService judgeService;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private Clock clock;

    private final List<Room> rooms = new ArrayList<>();

    @AfterEach
    void removeGames() {
        rooms.forEach(fixture::removeGame);
    }

    /** 진행 중인 사람전에서 두 사람이 하트를 1개씩 쓴 상태. */
    private Room started() {
        Room room = fixture.memberVsGuest(IN_CHAT);
        rooms.add(room);
        inTransaction(() -> {
            heartService.consume(room.sessionId(), room.hostUserId());
            heartService.consume(room.sessionId(), room.opponentUserId());
        });
        return room;
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private int hearts(Long userId) {
        return heartService.getMyHearts(userId).hearts();
    }

    private void assertHearts(Room room, int expected) {
        assertThat(hearts(room.hostUserId())).isEqualTo(expected);
        assertThat(hearts(room.opponentUserId())).isEqualTo(expected);
    }

    private boolean refunded(Room room) {
        return heartUseRepository.findUnrefundedUserIds(room.sessionId()).isEmpty();
    }

    /** 결과를 다시 조회합니다. 판정이 실패한 상태면 재판정이 시작됩니다. */
    private String pollResult(Room room) throws Exception {
        String body = mockMvc.perform(get("/api/v1/sessions/" + room.sessionId() + "/result")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtTokenProvider.createAccessToken(room.hostUserId(), Role.USER)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.status");
    }

    private JudgeStatus judgeStatus(Room room) {
        return gameRegistry.find(room.sessionId()).map(game -> game.judgeStatus()).orElse(null);
    }

    // ---- 무효 ----

    @Test
    @DisplayName("무효(양쪽 이탈 · 봇 실패)로 끝나면 두 사람 모두 돌려받는다")
    void abortRefunds() {
        Room room = started();
        assertHearts(room, 2);

        assertThat(gameExitService.abort(room.sessionId())).isTrue();

        assertHearts(room, 3);
        assertThat(refunded(room)).isTrue();
    }

    @Test
    @DisplayName("같은 판을 두 번 돌려줘도 한 번만 돌려받는다")
    void refundsOnce() {
        // 앞 판은 몰수패라 돌려받지 않아 2개, 이번 판에 1개를 더 써서 1개
        Room earlier = started();
        gameExitService.forfeit(earlier.sessionId(), earlier.hostParticipantId());
        Long userId = earlier.hostUserId();
        Room room = fixture.memberVsGuest(IN_CHAT);
        rooms.add(room);
        inTransaction(() -> heartService.consume(room.sessionId(), userId));
        assertThat(hearts(userId)).isEqualTo(1);

        inTransaction(() -> heartService.refund(room.sessionId()));
        inTransaction(() -> heartService.refund(room.sessionId()));

        assertThat(hearts(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("몰수패로 끝나면 두 사람 모두 돌려받지 않는다")
    void forfeitKeepsHeartsUsed() {
        Room room = started();

        assertThat(gameExitService.forfeit(room.sessionId(), room.hostParticipantId())).isTrue();

        assertHearts(room, 2);
        assertThat(refunded(room)).isFalse();
    }

    @Test
    @DisplayName("서버 재시작 정리로 무효가 된 게임도 돌려받는다")
    void restartCleanupRefunds() {
        Room room = started();
        gameRegistry.remove(room.sessionId());

        inProgressSessionCleaner.abortInProgressSessions(LocalDateTime.now(clock).plusSeconds(1));

        assertHearts(room, 3);
    }

    // ---- 판정 실패 ----

    @Test
    @DisplayName("재판정까지 모두 실패하면 마지막 실패 때 바로 돌려받는다")
    void lastRejudgeFailureRefunds() throws Exception {
        Room room = started();
        fixture.finish(room);
        doThrow(new IllegalStateException("AI 응답 없음")).when(judge).judge(any());

        judgeService.start(room.sessionId());
        waitUntil(() -> judgeStatus(room) == JudgeStatus.FAILED);
        assertHearts(room, 2);

        // 재판정 1번째 — 아직 남았으므로 돌려주지 않음
        assertThat(pollResult(room)).isEqualTo("PENDING");
        waitUntil(() -> judgeStatus(room) == JudgeStatus.FAILED);
        assertHearts(room, 2);

        // 재판정 2번째(마지막) — 실패하면 바로 돌려줌. 게임은 아직 메모리에 있음
        assertThat(pollResult(room)).isEqualTo("PENDING");
        waitUntil(() -> refunded(room));
        assertHearts(room, 3);
        assertThat(gameRegistry.find(room.sessionId())).isPresent();
        assertThat(pollResult(room)).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("재판정이 남았는데 아무도 다시 조회하지 않으면, 보관 시간이 끝나 지울 때 돌려받는다")
    void failedUntilRemovalRefunds() {
        Room room = started();
        fixture.finish(room);
        doThrow(new IllegalStateException("AI 응답 없음")).when(judge).judge(any());

        judgeService.start(room.sessionId());
        waitUntil(() -> judgeStatus(room) == JudgeStatus.FAILED);
        assertHearts(room, 2);

        waitUntil(() -> gameRegistry.find(room.sessionId()).isEmpty());
        assertHearts(room, 3);
    }

    @Test
    @DisplayName("판정이 끝나면 돌려받지 않는다")
    void judgedKeepsHeartsUsed() {
        Room room = started();
        fixture.finish(room);
        doAnswer(invocation -> {
            JudgeRequest request = invocation.getArgument(0);
            return new JudgeResult(request.participants().stream()
                    .map(p -> new JudgeResult.ParticipantScore(p.participantId(), each(20)))
                    .toList(), summaries());
        }).when(judge).judge(any());

        judgeService.start(room.sessionId());
        waitUntil(() -> judgeStatus(room) == JudgeStatus.READY);
        waitUntil(() -> gameRegistry.find(room.sessionId()).isEmpty());

        assertHearts(room, 2);
        assertThat(refunded(room)).isFalse();
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
            summaries.put(criterion, criterion + " 요약");
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
