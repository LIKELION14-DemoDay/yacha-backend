package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.domain.ai.JudgeRequest;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.heart.service.HeartService;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateResult;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.game.JudgeStatus;
import likelion.yacha_backend.domain.session.game.Verdict;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.global.config.GameSchedulerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판정 (명세 2-6). 260초에 정상 종료된 게임의 대화 전체를 {@link Judge} 에 넘겨 채점하고, 승패를 정해 기록합니다.
 *
 * <pre>
 * start ─▶ 판정 실행기 ─▶ Judge (최대 game.judge.max-attempts 번)
 *                          ├─ 성공: 총점 비교 → DB 승패 기록 → 메모리에 READY + 점수 · 요약
 *                          └─ 실패: 메모리에 FAILED (결과를 다시 조회하면 game.judge.max-rejudges 번까지 다시 시작)
 *          끝나면 결과 화면 보관 시간(game.judge.result-retention) 뒤 게임째 메모리에서 지움
 * </pre>
 *
 * <p><b>LLM 호출은 판정 실행기에서, 트랜잭션 밖에서</b> 합니다. 타이머 스레드 · DB 커넥션을 응답이 올 때까지 붙잡지 않습니다.
 *
 * <p><b>승패 기록</b> — 회원과 AI 참가자는 기록하고, 게스트는 기록하지 않습니다(NULL). 게스트도 보관 시간 안에는
 * 메모리의 결과로 결과 화면을 봅니다.
 *
 * <p><b>재시도</b> — 명세 2-9 대로 타임아웃 · 5xx · 429 만 다시 시도합니다. AI 구현이 {@link AiException} 으로
 * 다시 시도할 수 없다고 알리면(설정 누락 · 4xx 등) 남은 시도 없이 바로 실패로 끝냅니다. 그 밖의 예외는 실패 종류를
 * 알 수 없으므로 다시 시도합니다. 시도당 시간은 AI 구현의 HTTP 타임아웃이 정합니다.
 * 429 · 5xx 는 보통 몇 초 이어지므로 시도 사이에 {@code game.judge.retry-backoff} 부터 두 배씩 기다립니다.
 *
 * <p><b>하트</b> — 판정이 끝내 실패하면 그 판에 쓴 하트를 돌려줍니다. 끝내 실패한 때는 두 가지입니다.
 * <ul>
 *   <li>재판정 횟수를 다 쓴 판정이 실패했을 때 — 바로 돌려줍니다.</li>
 *   <li>재판정이 남았는데 아무도 결과를 다시 조회하지 않아, 보관 시간이 끝날 때까지 {@code FAILED} 일 때 — 지울 때 돌려줍니다.</li>
 * </ul>
 * 돌려주기는 사용 기록마다 한 번뿐이라 두 경우가 겹쳐도 한 번만 돌려줍니다. 바로 돌려주기가 실패해도 지울 때 다시 시도하고,
 * 지울 때도 실패하면 게임을 지우지 않고 보관 시간마다 {@value #REFUND_MAX_ATTEMPTS}번까지 다시 시도합니다.
 * 서버가 재시작되면 메모리의 게임과 함께 사라집니다.
 *
 * <p>대화 내용은 로그에 남기지 않습니다 (명세 1-5).
 */
@Slf4j
@Service
@EnableConfigurationProperties(JudgeProperties.class)
public class JudgeService {

    /** 보관 시간이 끝날 때 하트 돌려주기를 시도하는 최대 횟수. 실패하면 보관 시간마다 다시 시도합니다. */
    static final int REFUND_MAX_ATTEMPTS = 3;

    private final GameRegistry gameRegistry;
    private final GameTimers gameTimers;
    private final Judge judge;
    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor judgeExecutor;
    private final JudgeProperties properties;
    private final HeartService heartService;
    private final Clock clock;

    public JudgeService(GameRegistry gameRegistry, GameTimers gameTimers, Judge judge,
                        DebateSessionRepository sessionRepository, DebateParticipantRepository participantRepository,
                        PlatformTransactionManager transactionManager,
                        @Qualifier(GameSchedulerConfig.JUDGE_EXECUTOR) Executor judgeExecutor,
                        JudgeProperties properties, HeartService heartService, Clock clock) {
        this.gameRegistry = gameRegistry;
        this.gameTimers = gameTimers;
        this.judge = judge;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.judgeExecutor = judgeExecutor;
        this.properties = properties;
        this.heartService = heartService;
        this.clock = clock;
    }

    /**
     * 판정을 시작하고, 시작했으면 true 를 돌려줍니다. 260초 종료 처리와, 실패한 판정의 결과를 다시 조회할 때 부릅니다.
     * 게임이 메모리에 없거나(재시작 · 보관 시간 경과) 이미 판정 중 · 판정 완료거나, 재판정 횟수를 다 썼으면
     * 아무것도 하지 않고 false 입니다.
     */
    public boolean start(Long sessionId) {
        Game game = gameRegistry.find(sessionId).orElse(null);
        if (game == null || !game.startJudging(maxRuns())) {
            return false;
        }
        // 재판정이면 앞선 판정이 걸어 둔 삭제 타이머가 있습니다. 판정 도중에 게임이 지워지지 않게 먼저 지우고,
        // 판정이 끝나면 그때부터 보관 시간을 다시 셉니다.
        gameTimers.cancelAll(sessionId);
        try {
            judgeExecutor.execute(() -> run(sessionId, game));
        } catch (TaskRejectedException e) {
            log.error("[판정] 판정 대기열이 가득 차 시작하지 못했습니다. sessionId={}", sessionId);
            fail(sessionId, game);
            scheduleRemoval(sessionId);
        }
        return true;
    }

    private void run(Long sessionId, Game game) {
        try {
            Participants participants = transactionTemplate.execute(status -> participants(sessionId));
            JudgeResult judgeResult = judgeWithRetry(sessionId, new JudgeRequest(
                    participants.topic(), participants.judged(), AiConversation.of(game)));
            Verdict verdict = Verdict.of(
                    participants.judged().stream().map(JudgeRequest.Participant::participantId).toList(), judgeResult);
            transactionTemplate.executeWithoutResult(status -> recordResults(sessionId, verdict));
            game.completeJudging(verdict);
        } catch (RuntimeException e) {
            log.error("[판정] 판정에 실패했습니다. sessionId={}", sessionId, e);
            fail(sessionId, game);
        } finally {
            scheduleRemoval(sessionId);
        }
    }

    /**
     * 판정을 실패로 둡니다. 재판정 횟수를 다 썼으면 끝내 실패한 것이라 하트를 바로 돌려줍니다.
     * 판정 실행기가 마지막 재판정을 받지 못한 경우(대기열 가득 참)도 같습니다.
     */
    private void fail(Long sessionId, Game game) {
        game.failJudging();
        if (!game.hasJudgeRunsLeft(maxRuns())) {
            refundHearts(sessionId);
        }
    }

    private JudgeResult judgeWithRetry(Long sessionId, JudgeRequest request) {
        RuntimeException last = null;
        Duration backoff = properties.retryBackoff();
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            if (attempt > 1) {
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
            }
            try {
                return judge.judge(request);
            } catch (AiException e) {
                if (!e.isRetryable()) {
                    log.warn("[판정] 다시 시도할 수 없는 실패라 판정을 멈춥니다. sessionId={}, error={}",
                            sessionId, e.getMessage());
                    throw e;
                }
                last = e;
                logFailedAttempt(sessionId, attempt, e);
            } catch (RuntimeException e) {
                last = e;
                logFailedAttempt(sessionId, attempt, e);
            }
        }
        throw last;
    }

    private void logFailedAttempt(Long sessionId, int attempt, RuntimeException e) {
        log.warn("[판정] {}/{}번째 시도가 실패했습니다. sessionId={}, error={}",
                attempt, properties.maxAttempts(), sessionId, e.getClass().getSimpleName());
    }

    /** 재시도 전 대기. 서버 종료로 끊기면 남은 시도를 하지 않고 실패로 끝냅니다. */
    private static void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("판정 재시도 대기 중 중단됐습니다.", e);
        }
    }

    /** 판정 입력의 주제와 두 참가자. 엔티티는 트랜잭션 밖으로 내보내지 않습니다. */
    private Participants participants(Long sessionId) {
        DebateSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("세션이 없습니다. sessionId=" + sessionId));
        Topic topic = session.getTopic();
        List<JudgeRequest.Participant> judged = participantRepository.findAllBySession_Id(sessionId).stream()
                .map(p -> new JudgeRequest.Participant(p.getId(), p.getStance()))
                .toList();
        return new Participants(new AiTopic(topic.getStatement(), topic.getAgreeText(), topic.getDisagreeText()), judged);
    }

    /** 회원 · AI 참가자의 승패를 기록합니다. 게스트와 계정이 정리된 참가자는 건너뜁니다. */
    private void recordResults(Long sessionId, Verdict verdict) {
        for (DebateParticipant participant : participantRepository.findAllBySession_Id(sessionId)) {
            DebateResult result = verdict.resultOf(participant.getId());
            if (result == null || participant.getResult() != null || !participant.keepsRecord()) {
                continue;
            }
            participant.recordResult(result);
        }
    }

    /**
     * 결과 화면 보관 시간 뒤에 게임을 메모리에서 지웁니다. 다시 판정하면 그때부터 다시 셉니다.
     * 지우고 나면 {@code /result} 는 DB 의 승패만 줍니다.
     */
    private void scheduleRemoval(Long sessionId) {
        gameTimers.cancelAll(sessionId);
        scheduleRemoval(sessionId, 1);
    }

    private void scheduleRemoval(Long sessionId, int attempt) {
        gameTimers.schedule(sessionId, LocalDateTime.now(clock).plus(properties.resultRetention()),
                () -> removeAfterRetention(sessionId, attempt));
    }

    /**
     * 보관 시간이 끝난 게임을 지웁니다. 먼저 판정을 닫아({@link Game#closeJudging}) 그 뒤로는 재판정이 시작되지 않게 합니다.
     * 닫지 못했으면 결과 재조회로 재판정이 막 시작된 것이라 지우지 않습니다 — 그 판정이 끝나면 삭제 타이머를 다시 겁니다.
     * 닫았을 때 판정 실패면 끝내 실패한 것이라 쓴 하트를 돌려줍니다.
     *
     * <p>돌려주기가 실패하면(DB 일시 장애 등) 게임을 지우지 않고 보관 시간 뒤에 다시 시도합니다. 지우고 나면 판정 실패였다는
     * 사실이 사라져 다시 돌려줄 수 없기 때문입니다. {@value #REFUND_MAX_ATTEMPTS}번 모두 실패하면 로그를 남기고 지웁니다.
     *
     * @param attempt 이번이 몇 번째 시도인지 (1부터)
     */
    private void removeAfterRetention(Long sessionId, int attempt) {
        Game game = gameRegistry.find(sessionId).orElse(null);
        if (game == null || !game.closeJudging()) {
            return;
        }
        if (game.judgeStatus() == JudgeStatus.FAILED && !refundHearts(sessionId)) {
            if (attempt < REFUND_MAX_ATTEMPTS) {
                log.warn("[판정] 하트를 돌려주지 못해 게임을 남겨 두고 보관 시간 뒤 다시 시도합니다. ({}/{}) sessionId={}",
                        attempt, REFUND_MAX_ATTEMPTS, sessionId);
                scheduleRemoval(sessionId, attempt + 1);
                return;
            }
            log.error("[판정] 하트를 끝내 돌려주지 못하고 게임을 지웁니다. sessionId={}", sessionId);
        }
        gameRegistry.remove(sessionId);
    }

    /** 판정을 시작할 수 있는 최대 횟수 (처음 판정 + 재판정). */
    private int maxRuns() {
        return 1 + properties.maxRejudges();
    }

    /** 판정이 끝내 실패한 판의 하트를 돌려주고, 돌려줬으면 true 입니다. 실패해도 예외를 던지지 않습니다. */
    private boolean refundHearts(Long sessionId) {
        try {
            heartService.refund(sessionId);
            return true;
        } catch (RuntimeException e) {
            log.error("[판정] 판정 실패로 하트를 돌려주지 못했습니다. sessionId={}", sessionId, e);
            return false;
        }
    }

    private record Participants(AiTopic topic, List<JudgeRequest.Participant> judged) {
    }
}
