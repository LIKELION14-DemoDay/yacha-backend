package likelion.yacha_backend.domain.session.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.session.dto.HintResponse;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.global.config.GameSchedulerConfig;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 힌트 (명세 2-5). 주장 작성 화면의 "힌트 보기" 에 쓸, 내 입장에 맞는 짧은 문장 3개를 매칭 직후 만듭니다.
 *
 * <pre>
 * 게임 생성 ─▶ 사람 참가자마다 PENDING ─▶ 힌트 실행기 ─▶ HintGenerator (최대 game.hint.max-attempts 번)
 *                                                       ├─ 성공: READY + 3개
 *                                                       └─ 실패: FAILED (다시 만들지 않고 힌트 없이 진행)
 * </pre>
 *
 * <p><b>LLM 호출은 힌트 실행기에서, 트랜잭션 밖에서</b> 합니다. 입장 요청 · 타이머 스레드를 붙잡지 않습니다.
 * PREP 60초 안에 끝내는 것이 목표라 재시도는 짧게 둡니다. AI 구현이 {@link AiException} 으로 다시 시도할 수 없다고
 * 알리면(설정 누락 · 4xx) 바로 멈춥니다. 봇은 힌트를 보지 않으므로 사람 참가자 것만 만듭니다.
 *
 * <p>개수 · 글자 수 상한은 구현체가 지키지만({@link HintGenerator}), 화면이 깨지지 않게 받을 때 한 번 더 확인하고
 * 어기면 실패한 시도로 봅니다.
 */
@Slf4j
@Service
@EnableConfigurationProperties(HintProperties.class)
public class HintService {

    private final GameRegistry gameRegistry;
    private final HintGenerator hintGenerator;
    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor hintExecutor;
    private final HintProperties properties;

    public HintService(GameRegistry gameRegistry, HintGenerator hintGenerator,
                       DebateSessionRepository sessionRepository, DebateParticipantRepository participantRepository,
                       PlatformTransactionManager transactionManager,
                       @Qualifier(GameSchedulerConfig.HINT_EXECUTOR) Executor hintExecutor,
                       HintProperties properties) {
        this.gameRegistry = gameRegistry;
        this.hintGenerator = hintGenerator;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.hintExecutor = hintExecutor;
        this.properties = properties;
    }

    /** 게임의 사람 참가자마다 힌트를 만들기 시작합니다. 게임을 만든 직후(커밋 뒤) 부릅니다. */
    public void start(Long sessionId) {
        Game game = gameRegistry.find(sessionId).orElse(null);
        if (game == null) {
            return;
        }
        for (Long userId : game.humanUserIds()) {
            game.startHints(userId);
            try {
                hintExecutor.execute(() -> generate(sessionId, game, userId));
            } catch (TaskRejectedException e) {
                log.error("[힌트] 힌트 대기열이 가득 차 시작하지 못했습니다. sessionId={}", sessionId);
                game.failHints(userId);
            }
        }
    }

    /**
     * 내 힌트. 참가자 본인 것만, 게임 중에만 조회합니다.
     *
     * @throws BusinessException {@code SESSION_NOT_FOUND} 없는 세션, {@code SESSION_NOT_IN_PROGRESS} 게임 중이 아님,
     *                           {@code NOT_PARTICIPANT} 참가자가 아님
     */
    public HintResponse getMyHints(Long sessionId, Long userId) {
        Game game = gameRegistry.find(sessionId).filter(found -> !found.isFinished()).orElse(null);
        if (game == null) {
            throw new BusinessException(sessionRepository.existsById(sessionId)
                    ? SessionErrorCode.SESSION_NOT_IN_PROGRESS : SessionErrorCode.SESSION_NOT_FOUND);
        }
        if (!game.isParticipant(userId)) {
            throw new BusinessException(SessionErrorCode.NOT_PARTICIPANT);
        }
        return HintResponse.from(game.hintsOf(userId));
    }

    private void generate(Long sessionId, Game game, Long userId) {
        try {
            Input input = transactionTemplate.execute(status -> input(sessionId, game.participantIdOf(userId)));
            game.completeHints(userId, generateWithRetry(sessionId, input));
        } catch (RuntimeException e) {
            log.error("[힌트] 힌트를 만들지 못했습니다. sessionId={}", sessionId, e);
            game.failHints(userId);
        }
    }

    private List<String> generateWithRetry(Long sessionId, Input input) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            if (attempt > 1) {
                sleep(properties.retryBackoff());
            }
            try {
                return checked(hintGenerator.generate(input.topic(), input.stance()));
            } catch (AiException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                last = e;
            } catch (RuntimeException e) {
                last = e;
            }
            // 예외 메시지에는 힌트 본문이 없고 원인(개수 · 글자 수 · HTTP 상태 등)만 있습니다.
            log.warn("[힌트] {}/{}번째 시도가 실패했습니다. sessionId={}, error={}: {}",
                    attempt, properties.maxAttempts(), sessionId, last.getClass().getSimpleName(), last.getMessage());
        }
        throw last;
    }

    /**
     * 개수 · 글자 수 상한을 확인합니다. 이모지처럼 두 칸(char)을 쓰는 문자도 한 글자로 셉니다.
     * 실패 메시지는 원인별로 나누고, 로그에 남으므로 본문 없이 숫자만 넣습니다.
     */
    private static List<String> checked(List<String> hints) {
        if (hints == null) {
            throw new IllegalStateException("힌트가 비어 있습니다.");
        }
        if (hints.size() != HintGenerator.HINT_COUNT) {
            throw new IllegalStateException(
                    "힌트는 정확히 " + HintGenerator.HINT_COUNT + "개여야 합니다. 실제 개수: " + hints.size());
        }
        for (String hint : hints) {
            if (hint == null || hint.isBlank()) {
                throw new IllegalStateException("빈 힌트는 사용할 수 없습니다.");
            }
            int length = hint.codePointCount(0, hint.length());
            if (length > HintGenerator.HINT_MAX_LENGTH) {
                throw new IllegalStateException(
                        "힌트가 " + HintGenerator.HINT_MAX_LENGTH + "자를 초과했습니다. 실제 길이: " + length);
            }
        }
        return hints;
    }

    /** 재시도 전 대기. 서버 종료로 끊기면 남은 시도를 하지 않고 실패로 끝냅니다. */
    private static void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("힌트 재시도 대기 중 중단됐습니다.", e);
        }
    }

    /** 힌트 입력의 주제와 입장. 엔티티는 트랜잭션 밖으로 내보내지 않습니다. */
    private Input input(Long sessionId, Long participantId) {
        Topic topic = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("세션이 없습니다. sessionId=" + sessionId))
                .getTopic();
        Stance stance = participantRepository.findById(participantId)
                .map(DebateParticipant::getStance)
                .orElseThrow(() -> new IllegalStateException("참가자가 없습니다. participantId=" + participantId));
        return new Input(new AiTopic(topic.getStatement(), topic.getAgreeText(), topic.getDisagreeText()), stance);
    }

    private record Input(AiTopic topic, Stance stance) {
    }
}
