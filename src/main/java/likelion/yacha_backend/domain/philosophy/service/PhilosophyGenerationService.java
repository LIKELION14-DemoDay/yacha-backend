package likelion.yacha_backend.domain.philosophy.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.PhilosopherWriting;
import likelion.yacha_backend.domain.ai.PhilosophyWriter;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophySection;
import likelion.yacha_backend.domain.philosophy.exception.PhilosophyErrorCode;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyArticleRepository;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.exception.TopicErrorCode;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 1분 철학 글 생성
 *
 * 한 주에 정해진 편수(기본 5편)가 되도록 모자란 만큼 주제를 랜덤으로 고르고,
 * 주제마다 AI로 철학자 글을 만들어 검토 전({@code PENDING})으로 저장함. 공개는 팀원이 승인한 뒤
 *
 * 주제마다 따로 만들고 따로 저장해서, 일부가 실패해도 성공한 글은 남음. 모자란 편은 다시 부르면 채워짐
 * AI 호출은 수십 초까지 걸릴 수 있어 트랜잭션 밖에서 함. 주제 고르기와 저장만 각각 짧은 트랜잭션
 */
@Slf4j
@Service
@EnableConfigurationProperties(PhilosophyGenerationProperties.class)
public class PhilosophyGenerationService {

    private final PhilosophyArticleRepository articleRepository;
    private final TopicRepository topicRepository;
    private final PhilosophyWriter writer;
    private final TransactionTemplate transactionTemplate;
    private final RandomGenerator random;
    private final PhilosophyGenerationProperties properties;
    private final Clock clock;

    public PhilosophyGenerationService(PhilosophyArticleRepository articleRepository, TopicRepository topicRepository,
                                       PhilosophyWriter writer, PlatformTransactionManager transactionManager,
                                       RandomGenerator random, PhilosophyGenerationProperties properties,
                                       Clock clock) {
        this.articleRepository = articleRepository;
        this.topicRepository = topicRepository;
        this.writer = writer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.random = random;
        this.properties = properties;
        this.clock = clock;
    }

    /** 다음 주 월요일. 오늘이 월요일이면 일주일 뒤 */
    public LocalDate nextWeekStart() {
        return LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /**
     * {@code weekStart} 주의 글을 정해진 편수가 되도록 모자란 만큼 만들어 검토 전으로 저장
     *
     * 글 한 편이 실패해도 멈추지 않고 다음 주제로 넘어감. 키 오류처럼 다시 해도 안 되는 실패면 남은 주제도 멈춤
     *
     * @throws BusinessException 월요일이 아니면 {@code INVALID_WEEK_START}, 이미 다 있으면 {@code WEEK_ALREADY_FULL},
     *                           고를 주제가 하나도 없으면 {@code TOPIC_NOT_FOUND}
     */
    public PhilosophyGenerationResult generate(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(PhilosophyErrorCode.INVALID_WEEK_START);
        }
        int missing = properties.topicsPerWeek() - (int) articleRepository.countByWeekStart(weekStart);
        if (missing <= 0) {
            throw new BusinessException(PhilosophyErrorCode.WEEK_ALREADY_FULL);
        }

        List<PickedTopic> topics = transactionTemplate.execute(status -> pickTopics(weekStart, missing));

        List<Long> createdIds = new ArrayList<>();
        int failed = missing - topics.size();
        for (int i = 0; i < topics.size(); i++) {
            PickedTopic topic = topics.get(i);
            try {
                List<PhilosopherWriting> writings = writeWithRetry(topic);
                Long id = transactionTemplate.execute(status -> save(weekStart, topic.topicId(), writings));
                if (id == null) {
                    failed++;
                } else {
                    createdIds.add(id);
                }
            } catch (AiException e) {
                if (!e.isRetryable()) {
                    // 키 · 모델명 오류라 남은 주제도 똑같이 실패함. AI를 더 부르지 않음
                    failed += topics.size() - i;
                    break;
                }
                failed++;
            } catch (DataIntegrityViolationException e) {
                // AI를 기다리는 사이 같은 주에 같은 주제가 먼저 저장됨 (스케줄러와 관리자 생성이 겹친 경우)
                log.warn("[1분 철학] 같은 주에 같은 주제가 이미 있어 저장하지 않았습니다. weekStart={}, topicId={}",
                        weekStart, topic.topicId());
                failed++;
            } catch (RuntimeException e) {
                failed++;
            }
        }
        return new PhilosophyGenerationResult(createdIds, failed);
    }

    /**
     * 그 주에 쓸 주제를 {@code count}개까지, 서로 겹치지 않게 랜덤으로 고름
     * 아직 1분 철학에 쓰지 않은 활성 주제를 먼저 쓰고, 모자라면 그 주에 없는 활성 주제에서 채움
     * 활성 주제가 모자라면 고를 수 있는 만큼만 돌려줌
     */
    private List<PickedTopic> pickTopics(LocalDate weekStart, int count) {
        List<Long> picked = pickRandomly(articleRepository.findUnusedActiveTopicIds(), count);
        if (picked.size() < count) {
            Set<Long> excluded = new HashSet<>(articleRepository.findTopicIdsByWeekStart(weekStart));
            excluded.addAll(picked);
            List<Long> reusable = articleRepository.findActiveTopicIds().stream()
                    .filter(id -> !excluded.contains(id))
                    .toList();
            if (!reusable.isEmpty()) {
                log.info("[1분 철학] 안 쓴 주제가 모자라 이미 쓴 주제에서 다시 고릅니다. weekStart={}", weekStart);
            }
            picked.addAll(pickRandomly(reusable, count - picked.size()));
        }
        if (picked.isEmpty()) {
            throw new BusinessException(TopicErrorCode.TOPIC_NOT_FOUND);
        }
        return picked.stream()
                .map(id -> {
                    Topic topic = topicRepository.findById(id).orElseThrow();
                    return new PickedTopic(id,
                            new AiTopic(topic.getStatement(), topic.getAgreeText(), topic.getDisagreeText()));
                })
                .toList();
    }

    /** 후보에서 겹치지 않게 {@code count}개까지 고름 */
    private List<Long> pickRandomly(List<Long> candidates, int count) {
        List<Long> pool = new ArrayList<>(candidates);
        List<Long> picked = new ArrayList<>();
        while (picked.size() < count && !pool.isEmpty()) {
            picked.add(pool.remove(random.nextInt(pool.size())));
        }
        return picked;
    }

    /** 형식이 맞는 글이 나올 때까지 다시 만듦. 다시 시도할 수 없는 실패면 바로 멈춤 */
    private List<PhilosopherWriting> writeWithRetry(PickedTopic topic) {
        RuntimeException last = null;
        Duration backoff = properties.retryBackoff();
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            if (attempt > 1) {
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
            }
            try {
                return PhilosophyWritingValidator.validate(writer.write(topic.aiTopic()));
            } catch (AiException e) {
                if (!e.isRetryable()) {
                    log.warn("[1분 철학] 다시 시도할 수 없는 실패라 글 생성을 멈춥니다. topicId={}, error={}",
                            topic.topicId(), e.getMessage());
                    throw e;
                }
                last = e;
                logFailedAttempt(topic, attempt, e);
            } catch (RuntimeException e) {
                last = e;
                logFailedAttempt(topic, attempt, e);
            }
        }
        throw last;
    }

    private void logFailedAttempt(PickedTopic topic, int attempt, RuntimeException e) {
        log.warn("[1분 철학] 글 생성 {}/{}번째 시도가 실패했습니다. topicId={}, error={}",
                attempt, properties.maxAttempts(), topic.topicId(), e.getMessage());
    }

    /** 저장한 글 id. 그사이 그 주가 다 찼으면 저장하지 않고 null */
    private Long save(LocalDate weekStart, Long topicId, List<PhilosopherWriting> writings) {
        if (articleRepository.countByWeekStart(weekStart) >= properties.topicsPerWeek()) {
            log.warn("[1분 철학] 그사이 이 주가 다 차서 저장하지 않았습니다. weekStart={}, topicId={}", weekStart, topicId);
            return null;
        }
        PhilosophyArticle article = PhilosophyArticle.createPending(topicRepository.getReferenceById(topicId), weekStart);
        for (PhilosopherWriting writing : writings) {
            article.addContent(writing.philosopherName(), writing.sections().stream()
                    .map(section -> new PhilosophySection(section.subtitle(), section.body()))
                    .toList());
        }
        return articleRepository.saveAndFlush(article).getId();
    }

    /** 재시도 전 대기. 서버 종료로 끊기면 남은 시도를 하지 않고 실패로 끝냄 */
    private static void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("1분 철학 글 생성 재시도 대기 중 중단됐습니다.", e);
        }
    }

    /** 고른 주제. 엔티티는 트랜잭션 밖으로 내보내지 않음 */
    private record PickedTopic(Long topicId, AiTopic aiTopic) {
    }
}
