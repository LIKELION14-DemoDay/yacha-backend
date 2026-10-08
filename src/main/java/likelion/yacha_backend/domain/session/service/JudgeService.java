package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import likelion.yacha_backend.domain.ai.AiMessage;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.domain.ai.JudgeRequest;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.DebateResult;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameMessage;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.game.MessageType;
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
 *                          └─ 실패: 메모리에 FAILED (결과를 다시 조회하면 다시 시작)
 *          끝나면 결과 화면 보관 시간(game.judge.result-retention) 뒤 게임째 메모리에서 지움
 * </pre>
 *
 * <p><b>LLM 호출은 판정 실행기에서, 트랜잭션 밖에서</b> 합니다. 타이머 스레드 · DB 커넥션을 응답이 올 때까지 붙잡지 않습니다.
 *
 * <p><b>승패 기록</b> — 회원과 AI 참가자는 기록하고, 게스트는 기록하지 않습니다(NULL). 게스트도 보관 시간 안에는
 * 메모리의 결과로 결과 화면을 봅니다.
 *
 * <p><b>재시도</b> — 어떤 예외든 다시 시도합니다. 명세 2-9 는 타임아웃 · 5xx · 429 만 재시도하라고 하지만,
 * 지금은 AI 구현이 실패 종류를 구분해 알려 주지 않습니다. 시도당 시간은 AI 구현의 HTTP 타임아웃이 정합니다.
 *
 * <p>대화 내용은 로그에 남기지 않습니다 (명세 1-5).
 */
@Slf4j
@Service
@EnableConfigurationProperties(JudgeProperties.class)
public class JudgeService {

    private final GameRegistry gameRegistry;
    private final GameTimers gameTimers;
    private final Judge judge;
    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor judgeExecutor;
    private final JudgeProperties properties;
    private final Clock clock;

    public JudgeService(GameRegistry gameRegistry, GameTimers gameTimers, Judge judge,
                        DebateSessionRepository sessionRepository, DebateParticipantRepository participantRepository,
                        PlatformTransactionManager transactionManager,
                        @Qualifier(GameSchedulerConfig.JUDGE_EXECUTOR) Executor judgeExecutor,
                        JudgeProperties properties, Clock clock) {
        this.gameRegistry = gameRegistry;
        this.gameTimers = gameTimers;
        this.judge = judge;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.judgeExecutor = judgeExecutor;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 판정을 시작합니다. 260초 종료 처리와, 실패한 판정의 결과를 다시 조회할 때 부릅니다.
     * 게임이 메모리에 없거나(재시작 · 보관 시간 경과) 이미 판정 중 · 판정 완료면 아무것도 하지 않습니다.
     */
    public void start(Long sessionId) {
        Game game = gameRegistry.find(sessionId).orElse(null);
        if (game == null || !game.startJudging()) {
            return;
        }
        try {
            judgeExecutor.execute(() -> run(sessionId, game));
        } catch (TaskRejectedException e) {
            log.error("[판정] 판정 대기열이 가득 차 시작하지 못했습니다. sessionId={}", sessionId);
            game.failJudging();
            scheduleRemoval(sessionId);
        }
    }

    private void run(Long sessionId, Game game) {
        try {
            Participants participants = transactionTemplate.execute(status -> participants(sessionId));
            JudgeResult judgeResult = judgeWithRetry(sessionId, new JudgeRequest(
                    participants.topic(), participants.judged(), conversation(game)));
            Verdict verdict = Verdict.of(
                    participants.judged().stream().map(JudgeRequest.Participant::participantId).toList(), judgeResult);
            transactionTemplate.executeWithoutResult(status -> recordResults(sessionId, verdict));
            game.completeJudging(verdict);
        } catch (RuntimeException e) {
            log.error("[판정] 판정에 실패했습니다. sessionId={}", sessionId, e);
            game.failJudging();
        } finally {
            scheduleRemoval(sessionId);
        }
    }

    private JudgeResult judgeWithRetry(Long sessionId, JudgeRequest request) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return judge.judge(request);
            } catch (RuntimeException e) {
                last = e;
                log.warn("[판정] {}/{}번째 시도가 실패했습니다. sessionId={}, error={}",
                        attempt, properties.maxAttempts(), sessionId, e.getClass().getSimpleName());
            }
        }
        throw last;
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

    /** 공개된 주장 · 반론과 채팅 전체를 {@code seqNo} 순서대로. 공개되지 않은 글(빈 글)은 들어가지 않습니다. */
    private static List<AiMessage> conversation(Game game) {
        return game.messagesAfter(0).stream()
                .map(message -> new AiMessage(message.participantId(), kindOf(message), message.content()))
                .toList();
    }

    private static AiMessage.Kind kindOf(GameMessage message) {
        if (message.type() == MessageType.CHAT) {
            return AiMessage.Kind.CHAT;
        }
        return message.phase() == DebatePhase.PREP ? AiMessage.Kind.ARGUMENT : AiMessage.Kind.REBUTTAL;
    }

    /** 회원 · AI 참가자의 승패를 기록합니다. 게스트와 계정이 정리된 참가자는 건너뜁니다. */
    private void recordResults(Long sessionId, Verdict verdict) {
        for (DebateParticipant participant : participantRepository.findAllBySession_Id(sessionId)) {
            DebateResult result = verdict.resultOf(participant.getId());
            if (result == null || participant.getResult() != null || !keepsRecord(participant)) {
                continue;
            }
            participant.recordResult(result);
        }
    }

    private static boolean keepsRecord(DebateParticipant participant) {
        if (participant.isAi()) {
            return true;
        }
        return participant.getUser() != null && !participant.getUser().isGuest();
    }

    /**
     * 결과 화면 보관 시간 뒤에 게임을 메모리에서 지웁니다. 다시 판정하면 그때부터 다시 셉니다.
     * 지우고 나면 {@code /result} 는 DB 의 승패만 줍니다.
     */
    private void scheduleRemoval(Long sessionId) {
        gameTimers.cancelAll(sessionId);
        gameTimers.schedule(sessionId, LocalDateTime.now(clock).plus(properties.resultRetention()),
                () -> gameRegistry.remove(sessionId));
    }

    private record Participants(AiTopic topic, List<JudgeRequest.Participant> judged) {
    }
}
