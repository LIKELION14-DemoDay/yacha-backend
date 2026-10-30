package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.session.SessionFixture;
import likelion.yacha_backend.domain.session.SessionFixture.Room;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.HintStatus;
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
 * 힌트 (명세 2-5) — 매칭 직후 생성 · 재시도 · 조회. 실제 실행기로 확인합니다 ({@link TimerTestSupport}, 재시도 대기 10ms).
 * 기본은 임시 구현({@code StubHintGenerator})이고, 실패는 {@link #hintGenerator} 로 흉내 냅니다.
 */
@DisplayName("힌트 — 매칭 직후 생성 · 재시도 · GET /hints")
class HintTest extends TimerTestSupport {

    private static final long WAIT_MILLIS = 5_000;
    private static final List<String> HINTS = List.of("구체적인 사례", "상대의 약점", "결과와 영향");

    @Autowired
    private SessionFixture fixture;

    @Autowired
    private SessionMatchFacade sessionMatchFacade;

    @Autowired
    private GameRegistry gameRegistry;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final List<Long> sessionIds = new ArrayList<>();

    @AfterEach
    void removeGames() {
        sessionIds.forEach(gameRegistry::remove);
    }

    private ResultActions hints(Long userId, Long sessionId) throws Exception {
        return mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/hints").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtTokenProvider.createAccessToken(userId, Role.USER)));
    }

    private String statusOf(Long userId, Long sessionId) throws Exception {
        return JsonPath.read(hints(userId, sessionId).andReturn().getResponse().getContentAsString(), "$.data.status");
    }

    private void waitForStatus(Long userId, Long sessionId, HintStatus status) {
        waitUntil(() -> gameRegistry.find(sessionId).orElseThrow().hintsOf(userId).status() == status);
    }

    /** 사람끼리 매칭시켜 게임을 만듭니다. 방장은 찬성, 들어온 사람은 반대입니다. */
    private Long matched(Long hostId, Long opponentId) {
        Long sessionId = sessionMatchFacade.create(hostId,
                new SessionCreateRequest(RoomType.RANDOM, fixture.topic().getId(), Stance.AGREE));
        sessionIds.add(sessionId);
        sessionMatchFacade.join(opponentId, sessionId);
        return sessionId;
    }

    @Test
    @DisplayName("매칭되면 두 사람 모두 각자 입장의 힌트 3개를 받는다")
    void bothParticipantsGetHints() throws Exception {
        doReturn(HINTS).when(hintGenerator).generate(any(), any());
        Long hostId = fixture.newUser();
        Long opponentId = fixture.newUser();

        Long sessionId = matched(hostId, opponentId);

        for (Long userId : List.of(hostId, opponentId)) {
            waitForStatus(userId, sessionId, HintStatus.READY);
            hints(userId, sessionId)
                    .andExpect(jsonPath("$.data.status").value("READY"))
                    .andExpect(jsonPath("$.data.hints.length()").value(3))
                    .andExpect(jsonPath("$.data.hints[0]").value("구체적인 사례"));
        }
        verify(hintGenerator).generate(any(), eq(Stance.AGREE));
        verify(hintGenerator).generate(any(), eq(Stance.DISAGREE));
    }

    @Test
    @DisplayName("봇전은 사람 참가자 것만 만든다")
    void botMatchOnlyHuman() {
        Long userId = fixture.newUser();

        Long sessionId = startBot(sessionMatchFacade, fixture, userId, Stance.AGREE);
        sessionIds.add(sessionId);

        waitForStatus(userId, sessionId, HintStatus.READY);
        verify(hintGenerator, times(1)).generate(any(), eq(Stance.AGREE));
    }

    @Test
    @DisplayName("만드는 중이면 PENDING")
    void pendingWhileGenerating() throws Exception {
        // 생성 작업을 붙잡아 두고 조회한 뒤 풀어 줍니다. 조회가 늦어도 먼저 끝나지 않습니다.
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            release.await(WAIT_MILLIS, TimeUnit.MILLISECONDS);
            return HINTS;
        }).when(hintGenerator).generate(any(), any());
        Long userId = fixture.newUser();

        Long sessionId = startBot(sessionMatchFacade, fixture, userId, Stance.AGREE);
        sessionIds.add(sessionId);

        try {
            assertThat(started.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            hints(userId, sessionId)
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.hints").doesNotExist());
        } finally {
            release.countDown();
        }
        waitForStatus(userId, sessionId, HintStatus.READY);
    }

    @Test
    @DisplayName("한 번 실패하면 다시 시도해 READY")
    void retriesOnce() {
        doThrow(new AiException("LINER_HTTP_ERROR_503", true)).doReturn(HINTS).when(hintGenerator).generate(any(), any());
        Long userId = fixture.newUser();

        Long sessionId = startBot(sessionMatchFacade, fixture, userId, Stance.AGREE);
        sessionIds.add(sessionId);

        waitForStatus(userId, sessionId, HintStatus.READY);
        verify(hintGenerator, times(2)).generate(any(), any());
    }

    @Test
    @DisplayName("2번 모두 실패하면 FAILED 로 두고 다시 만들지 않는다")
    void failsAfterMaxAttempts() throws Exception {
        doThrow(new IllegalStateException("AI 응답 없음")).when(hintGenerator).generate(any(), any());
        Long userId = fixture.newUser();

        Long sessionId = startBot(sessionMatchFacade, fixture, userId, Stance.AGREE);
        sessionIds.add(sessionId);

        waitForStatus(userId, sessionId, HintStatus.FAILED);
        assertThat(statusOf(userId, sessionId)).isEqualTo("FAILED");
        assertThat(statusOf(userId, sessionId)).isEqualTo("FAILED");
        verify(hintGenerator, times(2)).generate(any(), any());
    }

    @Test
    @DisplayName("다시 시도할 수 없는 실패(AiException retryable=false)면 한 번만 부르고 FAILED")
    void stopsOnNonRetryable() {
        doThrow(new AiException("LINER_HTTP_ERROR_401", false)).when(hintGenerator).generate(any(), any());
        Long userId = fixture.newUser();

        Long sessionId = startBot(sessionMatchFacade, fixture, userId, Stance.AGREE);
        sessionIds.add(sessionId);

        waitForStatus(userId, sessionId, HintStatus.FAILED);
        verify(hintGenerator, times(1)).generate(any(), any());
    }

    @Test
    @DisplayName("개수 · 글자 수 상한을 어긴 응답은 실패한 시도로 본다")
    void rejectsMalformedHints() {
        doReturn(List.of("하나", "둘")).doReturn(List.of("하나", "둘", "가".repeat(HintGenerator.HINT_MAX_LENGTH + 1)))
                .when(hintGenerator).generate(any(), any());
        Long userId = fixture.newUser();

        Long sessionId = startBot(sessionMatchFacade, fixture, userId, Stance.AGREE);
        sessionIds.add(sessionId);

        waitForStatus(userId, sessionId, HintStatus.FAILED);
        verify(hintGenerator, times(2)).generate(any(), any());
    }

    @Test
    @DisplayName("참가자가 아니면 NOT_PARTICIPANT, 게임이 없으면 SESSION_NOT_IN_PROGRESS, 없는 세션이면 SESSION_NOT_FOUND")
    void errors() throws Exception {
        Room room = fixture.randomHuman(10);
        sessionIds.add(room.sessionId());
        hints(fixture.newUser(), room.sessionId()).andExpect(jsonPath("$.error.code").value("NOT_PARTICIPANT"));

        fixture.finish(room);
        hints(room.hostUserId(), room.sessionId())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_IN_PROGRESS"));
        Room waiting = fixture.waitingRandom();
        hints(waiting.hostUserId(), waiting.sessionId())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_IN_PROGRESS"));
        sessionMatchFacade.cancel(waiting.hostUserId(), waiting.sessionId());
        hints(room.hostUserId(), 999_999L).andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
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
