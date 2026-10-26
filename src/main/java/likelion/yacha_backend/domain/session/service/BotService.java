package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.BotWriter;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.BotProgress;
import likelion.yacha_backend.domain.session.game.BotProgress.Submission;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameMemo;
import likelion.yacha_backend.domain.session.game.GameMessage;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.game.MessageType;
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
 * 봇전의 봇 발언 (명세 2-4). 봇도 사람과 같은 시간표 · 글자 수로 주장 · 반론을 내고, 채팅에 대답합니다.
 *
 * <pre>
 *   0초        주장 쓰기 시작 ──▶ 20~50초 무작위 시각에 제출 (늦게 끝나면 끝나는 즉시)
 *  63초 공개    반론 쓰기 시작 ──▶ 100~130초 무작위 시각에 제출 (사람 주장 원문을 보고 씀)
 * 143초~260초  사람이 말하면 마지막 말 3초 뒤 한 번 대답 · 사람이 10초 동안 조용하면 첫마디 한 번
 * </pre>
 *
 * <p>봇 글은 사람 글과 똑같이 게임에 기록되고({@link Game#saveBotMemo} · {@link Game#appendBotChat}) 같은 이벤트로
 * 갑니다. 그래서 공개 · {@code /state} 의 {@code submitted} · {@code seqNo} 순서 · 판정 입력이 그대로 맞습니다.
 * 제출 시각을 무작위로 두는 것은 사람 화면에 "상대 작성 완료" 가 바로 떠서 봇 티가 나지 않게 하기 위해서입니다.
 *
 * <p><b>LLM 호출은 봇 실행기에서</b>, 시각 맞추기는 {@link GameTimers} 로 합니다. 모든 작업은 실행될 때 게임을
 * 다시 확인하고, 끝났거나(260초 · 나가기 · 몰수) 지워진 게임이면 결과를 버립니다. 게임이 끝나면 남은 타이머는
 * {@link GameTimers#cancelAll} 로 함께 지워집니다.
 *
 * <p><b>실패</b> — 주장 · 반론은 작성 구간이 끝날 때까지 다시 시도합니다. 대기는 {@code game.bot.retry-backoff} 부터
 * 두 배씩(상한 {@code retry-backoff-max}) 늘리고, 실행기 스레드를 잡지 않게 {@link GameTimers} 로 다음 시도를 예약합니다
 * (429 처럼 바로 오는 실패에 시도를 몰아 쓰지 않기 위해서입니다).
 * <ul>
 *   <li>제출 시각에 글이 아직 없으면 {@value #PLACEHOLDER} 를 먼저 내고, 글이 끝나면 덮어씁니다.</li>
 *   <li>작성 구간이 끝날 때(60초 · 140초)까지 못 쓰면 {@value #FAILURE_TEXT} 를 내고, 그 글이 공개된 뒤
 *       {@code game.bot.failure-end-delay} 가 지나면 게임을 무효({@code ABORTED}, 승패 NULL)로 끝냅니다.</li>
 * </ul>
 * 채팅 대답은 다시 시도하지 않고 그 대답만 건너뜁니다.
 *
 * <p>대화 내용은 로그에 남기지 않습니다 (명세 1-5).
 */
@Slf4j
@Service
@EnableConfigurationProperties(BotProperties.class)
public class BotService {

    /** 주장 제출 시각 — 시작 후 20 ~ 50초 사이 (PREP 은 60초까지). */
    private static final int ARGUMENT_SUBMIT_FROM = 20;
    private static final int ARGUMENT_SUBMIT_TO = 50;
    /** 반론 제출 시각 — 시작 후 100 ~ 130초 사이 (REBUTTAL 은 80 ~ 140초). */
    private static final int REBUTTAL_SUBMIT_FROM = 100;
    private static final int REBUTTAL_SUBMIT_TO = 130;
    /** 제출 시각에 글이 아직 없을 때 먼저 내는 임시 글. */
    static final String PLACEHOLDER = "토론봇이 생각중입니다.";
    /** 작성 구간이 끝날 때까지 글을 못 썼을 때 내는 글. 공개된 뒤 게임을 무효로 끝냅니다. */
    static final String FAILURE_TEXT = "죄송합니다, 토론봇이 글 생성에 실패하였습니다. 게임이 자동으로 종료됩니다.";

    private final GameRegistry gameRegistry;
    private final GameTimers gameTimers;
    private final GameMessageService gameMessageService;
    private final GameExitService gameExitService;
    private final BotWriter botWriter;
    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor botExecutor;
    private final BotProperties properties;
    private final RandomGenerator random;
    private final Clock clock;

    public BotService(GameRegistry gameRegistry, GameTimers gameTimers, GameMessageService gameMessageService,
                      GameExitService gameExitService, BotWriter botWriter, DebateSessionRepository sessionRepository,
                      DebateParticipantRepository participantRepository, PlatformTransactionManager transactionManager,
                      @Qualifier(GameSchedulerConfig.BOT_EXECUTOR) Executor botExecutor, BotProperties properties,
                      RandomGenerator random, Clock clock) {
        this.gameRegistry = gameRegistry;
        this.gameTimers = gameTimers;
        this.gameMessageService = gameMessageService;
        this.gameExitService = gameExitService;
        this.botWriter = botWriter;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.botExecutor = botExecutor;
        this.properties = properties;
        this.random = random;
        this.clock = clock;
    }

    /**
     * 봇전의 봇 일정을 등록합니다. 게임을 만든 직후(커밋 뒤) 부릅니다. 사람전이면 아무것도 하지 않습니다.
     * 이미 지난 구간의 글은 쓰지 않습니다 (공개 시각이 지났으면 낼 수 없음).
     */
    public void start(Long sessionId) {
        Game game = gameRegistry.find(sessionId).filter(found -> found.botProgress() != null).orElse(null);
        if (game == null) {
            return;
        }
        LocalDateTime startedAt = game.getStartedAt();
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(DebatePhase.PREP.revealAt(startedAt))) {
            execute(sessionId, () -> write(sessionId, game, DebatePhase.PREP, 1));
            gameTimers.schedule(sessionId, startedAt.plusSeconds(random.nextInt(ARGUMENT_SUBMIT_FROM, ARGUMENT_SUBMIT_TO + 1)),
                    () -> due(sessionId, game, DebatePhase.PREP));
            gameTimers.schedule(sessionId, DebatePhase.PREP.endAt(startedAt), () -> fail(sessionId, game, DebatePhase.PREP));
        }
        if (now.isBefore(DebatePhase.REBUTTAL.revealAt(startedAt))) {
            // 반론은 사람 주장 원문을 봐야 하므로 주장이 공개되는 63초에 쓰기 시작합니다.
            gameTimers.schedule(sessionId, DebatePhase.PREP.revealAt(startedAt),
                    () -> execute(sessionId, () -> write(sessionId, game, DebatePhase.REBUTTAL, 1)));
            gameTimers.schedule(sessionId, startedAt.plusSeconds(random.nextInt(REBUTTAL_SUBMIT_FROM, REBUTTAL_SUBMIT_TO + 1)),
                    () -> due(sessionId, game, DebatePhase.REBUTTAL));
            gameTimers.schedule(sessionId, DebatePhase.REBUTTAL.endAt(startedAt),
                    () -> fail(sessionId, game, DebatePhase.REBUTTAL));
        }
        gameTimers.schedule(sessionId, DebatePhase.REBUTTAL.revealAt(startedAt).plus(properties.firstMessageDelay()),
                () -> firstMessage(sessionId, game));
    }

    /** 사람이 채팅했습니다. 봇전이면 {@code game.bot.reply-delay} 뒤에, 그때까지 또 말하지 않았으면 대답합니다. */
    public void humanSpoke(Long sessionId) {
        Game game = activeBotGame(sessionId);
        if (game == null) {
            return;
        }
        long chatNo = game.botProgress().humanSpoke();
        gameTimers.schedule(sessionId, LocalDateTime.now(clock).plus(properties.replyDelay()), () -> {
            if (isActive(game) && game.botProgress().startReply(chatNo)) {
                startReplying(sessionId, game);
            }
        });
    }

    // ---- 주장 · 반론 ----

    /**
     * 글 한 번 시도. 실패하면 작성 구간이 끝나기 전에 다음 시도를 예약합니다. 다시 시도할 수 없는 실패
     * ({@link AiException#isRetryable()} false)면 그만두고, 구간이 끝날 때 {@link #fail} 이 실패 글을 냅니다.
     */
    private void write(Long sessionId, Game game, DebatePhase phase, int attempt) {
        if (!isActive(game)) {
            return;
        }
        String text;
        try {
            Input input = input(sessionId, game);
            text = phase == DebatePhase.PREP
                    ? botWriter.writeArgument(input.topic(), input.stance())
                    : botWriter.writeRebuttal(input.topic(), input.stance(), humanArgument(sessionId, game));
            if (text == null) {
                throw new IllegalStateException("봇 글이 비어 있습니다.");
            }
        } catch (AiException e) {
            if (!e.isRetryable()) {
                log.error("[봇] {} 을 다시 시도할 수 없는 오류로 쓰지 못했습니다. sessionId={}", phase, sessionId, e);
                return;
            }
            retry(sessionId, game, phase, attempt, e);
            return;
        } catch (RuntimeException e) {
            retry(sessionId, game, phase, attempt, e);
            return;
        }
        submit(sessionId, game, phase, progress -> progress.prepared(phase, text));
    }

    /** 다음 시도를 예약합니다. 대기 뒤가 작성 구간 끝을 넘으면 예약하지 않습니다 ({@link #fail} 이 맡음). */
    private void retry(Long sessionId, Game game, DebatePhase phase, int attempt, RuntimeException cause) {
        LocalDateTime next = LocalDateTime.now(clock).plus(backoff(attempt));
        if (!next.isBefore(phase.endAt(game.getStartedAt()))) {
            log.error("[봇] {} 을 작성 구간 안에 쓰지 못했습니다. 시도 {}번, sessionId={}", phase, attempt, sessionId, cause);
            return;
        }
        log.warn("[봇] {} {}번째 시도가 실패해 다시 시도합니다. sessionId={}, error={}",
                phase, attempt, sessionId, cause.getClass().getSimpleName());
        gameTimers.schedule(sessionId, next,
                () -> execute(sessionId, () -> write(sessionId, game, phase, attempt + 1)));
    }

    /** {@code attempt} 번째 실패 뒤 대기 — 첫 대기부터 두 배씩, 상한까지. */
    private Duration backoff(int attempt) {
        Duration backoff = properties.retryBackoff().multipliedBy(1L << Math.min(attempt - 1, 20));
        return backoff.compareTo(properties.retryBackoffMax()) > 0 ? properties.retryBackoffMax() : backoff;
    }

    private void due(Long sessionId, Game game, DebatePhase phase) {
        submit(sessionId, game, phase, progress -> progress.due(phase, PLACEHOLDER));
    }

    /**
     * 작성 구간이 끝났습니다. 글을 내지 못했으면 실패 글을 내고, 그 글이 공개된 뒤 게임을 무효로 끝냅니다.
     * 그 사이 사람이 실패 글을 읽을 수 있게 {@code failure-end-delay} 만큼 기다립니다.
     */
    private void fail(Long sessionId, Game game, DebatePhase phase) {
        if (!isActive(game) || !submit(sessionId, game, phase, progress -> progress.fail(phase, FAILURE_TEXT))) {
            return;
        }
        log.error("[봇] {} 을 끝내 쓰지 못해 게임을 무효로 끝냅니다. sessionId={}", phase, sessionId);
        gameTimers.schedule(sessionId, phase.revealAt(game.getStartedAt()).plus(properties.failureEndDelay()), () -> {
            synchronized (game) {
                // 공개 스케줄러보다 먼저 왔어도 실패 글은 보이고 끝나게 합니다.
                gameMessageService.broadcastAll(sessionId, game.revealArguments(LocalDateTime.now(clock)));
            }
            gameExitService.abort(sessionId);
        });
    }

    /**
     * 봇 글을 제출합니다. 무엇을 낼지({@code decide})는 게임 락 안에서 정해, 늦게 정한 글이 먼저 들어가 덮어쓰이지
     * 않게 합니다. 처음 내는 글이면 사람처럼 {@code ARGUMENT_SUBMITTED} 를 보내고, 임시 글을 덮어쓸 때는 보내지 않습니다.
     * 지금이 그 글의 작성 구간(유예 포함)이 아니면 버립니다 — 늦게 끝난 주장이 반론으로 들어가거나,
     * 공개가 끝난 뒤 들어가지 않게 합니다.
     *
     * @return 글을 게임에 넣었으면 true
     */
    private boolean submit(Long sessionId, Game game, DebatePhase phase, Function<BotProgress, Submission> decide) {
        synchronized (game) {
            LocalDateTime now = LocalDateTime.now(clock);
            gameMessageService.broadcastAll(sessionId, game.revealArguments(now));
            if (DebatePhase.memoPhaseAt(game.getStartedAt(), now) != phase) {
                log.warn("[봇] {} 이 작성 구간을 넘겨 내지 않습니다. sessionId={}", phase, sessionId);
                return false;
            }
            Submission submission = decide.apply(game.botProgress());
            if (submission == null) {
                return false;
            }
            try {
                GameMemo memo = game.saveBotMemo(submission.text(), now);
                if (submission.announce()) {
                    gameMessageService.broadcastSubmitted(sessionId, game.getAiParticipantId(), memo.phase());
                }
                return true;
            } catch (BusinessException e) {
                game.botProgress().rejected(phase);
                log.warn("[봇] {} 을 내지 못했습니다. sessionId={}, error={}", phase, sessionId, e.getErrorCode().name());
                return false;
            }
        }
    }

    /** 공개된 사람 주장 원문. 사람이 내지 않았으면 빈 문자열입니다. 공개 시각에 맞춰 먼저 공개해 둡니다. */
    private String humanArgument(Long sessionId, Game game) {
        synchronized (game) {
            gameMessageService.broadcastAll(sessionId, game.revealArguments(LocalDateTime.now(clock)));
            return game.messagesAfter(0).stream()
                    .filter(message -> message.type() == MessageType.ARGUMENT && message.phase() == DebatePhase.PREP)
                    .filter(message -> !message.participantId().equals(game.getAiParticipantId()))
                    .map(GameMessage::content)
                    .findFirst()
                    .orElse("");
        }
    }

    // ---- 채팅 ----

    /** 첫마디 — 채팅이 열리고 사람이 아직 한 번도 말하지 않았으면 봇이 먼저 한 번 말합니다. */
    private void firstMessage(Long sessionId, Game game) {
        if (isActive(game) && game.botProgress().startFirstMessage()) {
            startReplying(sessionId, game);
        }
    }

    private void startReplying(Long sessionId, Game game) {
        if (!execute(sessionId, () -> reply(sessionId, game))) {
            game.botProgress().finishReply();
        }
    }

    /** 대답 하나. 끝나면, 만드는 동안 사람이 또 말해 차례가 왔으면 바로 한 번 더 대답합니다. */
    private void reply(Long sessionId, Game game) {
        try {
            if (isActive(game) && game.botChatCount() < properties.maxReplies()) {
                Input input = input(sessionId, game);
                String text = botWriter.reply(input.topic(), input.stance(), game.getAiParticipantId(),
                        AiConversation.of(game));
                synchronized (game) {
                    LocalDateTime now = LocalDateTime.now(clock);
                    gameMessageService.broadcastAll(sessionId, game.revealArguments(now));
                    gameMessageService.broadcastAll(sessionId, List.of(game.appendBotChat(text, now)));
                }
            }
        } catch (BusinessException e) {
            // 채팅이 닫혔거나(260초 · 종료) 글자 수 · 채팅 수를 넘었습니다. 이 대답만 버립니다.
            log.info("[봇] 대답을 버립니다. sessionId={}, error={}", sessionId, e.getErrorCode().name());
        } catch (RuntimeException e) {
            log.warn("[봇] 대답을 만들지 못해 건너뜁니다. sessionId={}, error={}", sessionId, e.getClass().getSimpleName());
        } finally {
            if (game.botProgress().finishReply()) {
                startReplying(sessionId, game);
            }
        }
    }

    // ---- 공통 ----

    /** 봇 실행기에 넘깁니다. 대기열이 가득 차면 건너뛰고 false 입니다. */
    private boolean execute(Long sessionId, Runnable task) {
        try {
            botExecutor.execute(task);
            return true;
        } catch (TaskRejectedException e) {
            log.error("[봇] 봇 대기열이 가득 차 건너뜁니다. sessionId={}", sessionId);
            return false;
        }
    }

    private Game activeBotGame(Long sessionId) {
        return gameRegistry.find(sessionId)
                .filter(game -> game.botProgress() != null && !game.isFinished() && !game.botProgress().isFailed())
                .orElse(null);
    }

    /**
     * 게임이 아직 저장소에 있고 끝나지 않았는가. 지워졌으면(몰수 · 무효 · 정리) 결과를 버립니다.
     * 봇이 글을 못 써 무효로 끝내는 중이어도 false 입니다 — 남은 몇 초 동안 쓰거나 대답하지 않습니다.
     */
    private boolean isActive(Game game) {
        return !game.isFinished() && !game.botProgress().isFailed()
                && gameRegistry.find(game.getSessionId()).filter(found -> found == game).isPresent();
    }

    /** 봇 글 입력의 주제와 봇 입장. 엔티티는 트랜잭션 밖으로 내보내지 않습니다. */
    private Input input(Long sessionId, Game game) {
        return transactionTemplate.execute(status -> {
            Topic topic = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalStateException("세션이 없습니다. sessionId=" + sessionId))
                    .getTopic();
            Stance stance = participantRepository.findById(game.getAiParticipantId())
                    .map(DebateParticipant::getStance)
                    .orElseThrow(() -> new IllegalStateException("AI 참가자가 없습니다. sessionId=" + sessionId));
            return new Input(new AiTopic(topic.getStatement(), topic.getAgreeText(), topic.getDisagreeText()), stance);
        });
    }

    private record Input(AiTopic topic, Stance stance) {
    }
}
