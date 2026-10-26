package likelion.yacha_backend.domain.philosophy.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import likelion.yacha_backend.domain.philosophy.dto.AdminArticleResponse;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.exception.PhilosophyErrorCode;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyArticleRepository;
import likelion.yacha_backend.global.config.AsyncConfig;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1분 철학 검토 · 승인 (#98). 관리자(ADMIN)만 부름
 *
 * 생성 · 다시 생성은 AI를 여러 번 불러 오래 걸려서, 요청에서는 확인만 하고 {@link AsyncConfig#PHILOSOPHY_EXECUTOR}에서 이어서 돎
 * 결과는 몇 분 뒤 주별 목록으로 확인. 실패는 서버 로그에 남음
 */
@Slf4j
@Service
public class PhilosophyAdminService {

    private final PhilosophyArticleRepository articleRepository;
    private final PhilosophyGenerationService generationService;
    private final PhilosophyGenerationProperties properties;
    private final TaskExecutor executor;
    private final Clock clock;

    public PhilosophyAdminService(PhilosophyArticleRepository articleRepository,
                                  PhilosophyGenerationService generationService,
                                  PhilosophyGenerationProperties properties,
                                  @Qualifier(AsyncConfig.PHILOSOPHY_EXECUTOR) TaskExecutor executor,
                                  Clock clock) {
        this.articleRepository = articleRepository;
        this.generationService = generationService;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    /** {@code weekStart}가 null이면 다음 주 */
    public LocalDate weekStartOrNext(LocalDate weekStart) {
        return weekStart != null ? weekStart : generationService.nextWeekStart();
    }

    /** 그 주의 글 전체 (검토 전 · 승인됨 모두), 만든 순서대로 */
    @Transactional(readOnly = true)
    public List<AdminArticleResponse> getWeek(LocalDate weekStart) {
        requireMonday(weekStart);
        return articleRepository.findAllByWeekStartOrderByIdAsc(weekStart).stream()
                .map(AdminArticleResponse::from)
                .toList();
    }

    /** 승인. 이미 승인된 글이면 그대로 둠 */
    @Transactional
    public void approve(Long articleId) {
        PhilosophyArticle article = find(articleId);
        if (!article.isApproved()) {
            article.approve(LocalDateTime.now(clock));
        }
    }

    /** 주제째 빼기. 모자란 편은 생성으로 다른 주제를 채움 */
    @Transactional
    public void delete(Long articleId) {
        PhilosophyArticle article = find(articleId);
        requireNotPublished(article);
        articleRepository.delete(article);
    }

    /**
     * 그 주에 모자란 만큼 생성을 시작함
     * 월요일인지 · 지난 주인지 · 이미 다 찼는지는 여기서 바로 확인해 에러로 알림
     */
    public void startGenerate(LocalDate weekStart) {
        requireMonday(weekStart);
        LocalDate thisWeek = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (weekStart.isBefore(thisWeek)) {
            throw new BusinessException(PhilosophyErrorCode.PAST_WEEK);
        }
        if (articleRepository.countByWeekStart(weekStart) >= properties.topicsPerWeek()) {
            throw new BusinessException(PhilosophyErrorCode.WEEK_ALREADY_FULL);
        }
        submit(() -> {
            PhilosophyGenerationResult result = generationService.generate(weekStart);
            log.info("[1분 철학] 관리자 생성: {} 주 글을 {}편 만들고 {}편은 만들지 못했습니다. ids={}",
                    weekStart, result.createdIds().size(), result.failed(), result.createdIds());
        }, "생성 weekStart=" + weekStart);
    }

    /**
     * 같은 주제로 다시 생성을 시작함. 글이 있는지 · 공개됐는지는 여기서 바로 확인
     * 트랜잭션을 걸지 않음. 테스트처럼 같은 스레드에서 바로 돌 때 읽기 전용 트랜잭션에 묶여 저장이 안 되지 않게
     */
    public void startRegenerate(Long articleId) {
        requireNotPublished(find(articleId));
        submit(() -> {
            generationService.regenerate(articleId);
            log.info("[1분 철학] 관리자 다시 생성: 글을 다시 만들었습니다. 검토 후 승인해 주세요. id={}", articleId);
        }, "다시 생성 id=" + articleId);
    }

    private void submit(Runnable task, String label) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("[1분 철학] 관리자 {} 실패", label, e);
                }
            });
        } catch (TaskRejectedException e) {
            throw new BusinessException(PhilosophyErrorCode.GENERATION_IN_PROGRESS);
        }
    }

    private PhilosophyArticle find(Long articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(PhilosophyErrorCode.ARTICLE_NOT_FOUND));
    }

    private void requireNotPublished(PhilosophyArticle article) {
        if (article.isPublished(LocalDate.now(clock))) {
            throw new BusinessException(PhilosophyErrorCode.ARTICLE_ALREADY_PUBLISHED);
        }
    }

    private static void requireMonday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(PhilosophyErrorCode.INVALID_WEEK_START);
        }
    }
}
