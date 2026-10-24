package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.BotWriter;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.game.BotProgress;
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
 * <p><b>실패</b> — 주장 · 반론은 최대 {@code game.bot.max-attempts} 번 시도하고, 끝내 실패하거나 공개 시각을 넘기면
 * 내지 않습니다(빈 글과 같음, 공개되지 않음). 채팅 대답은 다시 시도하지 않고 그 대답만 건너뜁니다.
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

    private final GameRegistry gameRegistry;
    private final GameTimers gameTimers;
    private final GameMessageService gameMessageService;
    private final BotWriter botWriter;
    private final DebateSessionRepository sessionRepository;
    private final DebateParticipantRepository participantRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor botExecutor;
    private final BotProperties properties;
    private final RandomGenerator random;
    private final Clock clock;

    public BotService(GameRegistry gameRegistry, GameTimers gameTimers, GameMessageService gameMessageService,
                      BotWriter botWriter, DebateSessionRepository sessionRepository,
                      DebateParticipantRepository participantRepository, PlatformTransactionManager transactionManager,
                      @Qualifier(GameSchedulerConfig.BOT_EXECUTOR) Executor botExecutor, BotProperties properties,
                      RandomGenerator random, Clock clock) {
        this.gameRegistry = gameRegistry;
        this.gameTimers = gameTimers;
        this.gameMessageService = gameMessageService;
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
            execute(sessionId, () -> write(sessionId, game, DebatePhase.PREP));
            gameTimers.schedule(sessionId, startedAt.plusSeconds(random.nextInt(ARGUMENT_SUBMIT_FROM, ARGUMENT_SUBMIT_TO + 1)),
                    () -> due(sessionId, game, DebatePhase.PREP));
        }
        if (now.isBefore(DebatePhase.REBUTTAL.revealAt(startedAt))) {
            // 반론은 사람 주장 원문을 봐야 하므로 주장이 공개되는 63초에 쓰기 시작합니다.
            gameTimers.schedule(sessionId, DebatePhase.PREP.revealAt(startedAt),
                    () -> execute(sessionId, () -> write(sessionId, game, DebatePhase.REBUTTAL)));
            gameTimers.schedule(sessionId, startedAt.plusSeconds(random.nextInt(REBUTTAL_SUBMIT_FROM, REBUTTAL_SUBMIT_TO + 1)),
                    () -> due(sessionId, game, DebatePhase.REBUTTAL));
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

    private void write(Long sessionId, Game game, DebatePhase phase) {
        if (!isActive(game)) {
            return;
        }
        String text;
        try {
            Input input = input(sessionId, game);
            text = writeWithRetry(sessionId, phase,
                    () -> phase == DebatePhase.PREP
                            ? botWriter.writeArgument(input.topic(), input.stance())
                            : botWriter.writeRebuttal(input.topic(), input.stance(), humanArgument(sessionId, game)));
        } catch (RuntimeException e) {
            log.error("[봇] {} 을 쓰지 못해 내지 않습니다. sessionId={}", phase, sessionId, e);
            return;
        }
        String ready = game.botProgress().prepared(phase, text);
        if (ready != null) {
            submit(sessionId, game, phase, ready);
        }
    }

    private void due(Long sessionId, Game game, DebatePhase phase) {
        String ready = game.botProgress().due(phase);
        if (ready != null) {
            submit(sessionId, game, phase, ready);
        }
    }

    /**
     * 봇 글을 제출하고 사람처럼 {@code ARGUMENT_SUBMITTED} 를 보냅니다. 지금이 그 글의 작성 구간(유예 포함)이
     * 아니면 버립니다 — 늦게 끝난 주장이 반론으로 들어가거나, 공개가 끝난 뒤 들어가지 않게 합니다.
     */
    private void submit(Long sessionId, Game game, DebatePhase phase, String text) {
        synchronized (game) {
            LocalDateTime now = LocalDateTime.now(clock);
            gameMessageService.broadcastAll(sessionId, game.revealArguments(now));
            if (DebatePhase.memoPhaseAt(game.getStartedAt(), now) != phase) {
                log.warn("[봇] {} 이 작성 구간을 넘겨 내지 않습니다. sessionId={}", phase, sessionId);
                return;
            }
            try {
                GameMemo memo = game.saveBotMemo(text, now);
                gameMessageService.broadcastSubmitted(sessionId, game.getAiParticipantId(), memo.phase());
            } catch (BusinessException e) {
                log.warn("[봇] {} 을 내지 못했습니다. sessionId={}, error={}", phase, sessionId, e.getErrorCode().name());
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

    private String writeWithRetry(Long sessionId, DebatePhase phase, Supplier<String> writer) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            if (attempt > 1) {
                sleep(properties.retryBackoff());
            }
            try {
                String text = writer.get();
                if (text == null) {
                    throw new IllegalStateException("봇 글이 비어 있습니다.");
                }
                return text;
            } catch (AiException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                last = e;
            } catch (RuntimeException e) {
                last = e;
            }
            log.warn("[봇] {} {}/{}번째 시도가 실패했습니다. sessionId={}, error={}",
                    phase, attempt, properties.maxAttempts(), sessionId, last.getClass().getSimpleName());
        }
        throw last;
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
                .filter(game -> game.botProgress() != null && !game.isFinished())
                .orElse(null);
    }

    /** 게임이 아직 저장소에 있고 끝나지 않았는가. 지워졌으면(몰수 · 무효 · 정리) 결과를 버립니다. */
    private boolean isActive(Game game) {
        return !game.isFinished() && gameRegistry.find(game.getSessionId()).filter(found -> found == game).isPresent();
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

    /** 재시도 전 대기. 서버 종료로 끊기면 남은 시도를 하지 않고 실패로 끝냅니다. */
    private static void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("봇 재시도 대기 중 중단됐습니다.", e);
        }
    }

    private record Input(AiTopic topic, Stance stance) {
    }
}
