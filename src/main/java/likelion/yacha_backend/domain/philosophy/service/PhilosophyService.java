package likelion.yacha_backend.domain.philosophy.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyCardResponse;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyCardsResponse;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyDetailResponse;
import likelion.yacha_backend.domain.philosophy.dto.ThisWeekPhilosophyResponse;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyMemo;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyStatus;
import likelion.yacha_backend.domain.philosophy.exception.PhilosophyErrorCode;
import likelion.yacha_backend.domain.philosophy.repository.ArticlePhilosopherName;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyArticleRepository;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyBookmarkRepository;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyMemoRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1분 철학 보기 (#98). 회원 전용 ({@code /philosophy/**}는 SecurityConfig 기본 규칙으로 회원만)
 *
 * 사용자에게는 공개된 글만 보임. 공개 = 승인됐고 그 주가 시작됨
 * "이번 주" = 공개된 글이 있는 주 중 가장 최근 주
 * 다음 주 글이 하나도 승인되지 않은 채 월요일이 와도 지난주 글이 계속 보여 화면이 비지 않음
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhilosophyService {

    /** 지난 글 한 페이지의 카드 수 */
    static final int PAGE_SIZE = 20;

    private final PhilosophyArticleRepository articleRepository;
    private final PhilosophyMemoRepository memoRepository;
    private final PhilosophyBookmarkRepository bookmarkRepository;
    private final Clock clock;

    public ThisWeekPhilosophyResponse getThisWeek(Long userId) {
        LocalDate week = currentWeek();
        if (week == null) {
            return new ThisWeekPhilosophyResponse(null, List.of());
        }
        List<PhilosophyArticle> articles = articleRepository.findAllByWeekWithTopic(week, PhilosophyStatus.APPROVED);
        return new ThisWeekPhilosophyResponse(week, toCards(userId, articles));
    }

    /** 이번 주보다 앞선 주의 공개된 글, 최신 주부터 한 페이지 {@value #PAGE_SIZE}개 ({@code page}는 0부터) */
    public PhilosophyCardsResponse getPast(Long userId, int page) {
        if (page < 0) {
            throw new BusinessException(GlobalErrorCode.VALIDATION_FAILED);
        }
        LocalDate week = currentWeek();
        if (week == null) {
            return new PhilosophyCardsResponse(List.of(), false);
        }
        Slice<PhilosophyArticle> articles = articleRepository.findAllBeforeWithTopic(
                week, PhilosophyStatus.APPROVED, PageRequest.of(page, PAGE_SIZE));
        return new PhilosophyCardsResponse(toCards(userId, articles.getContent()), articles.hasNext());
    }

    /** 공개된 글만. 승인됐어도 그 주가 시작되기 전이면 없는 글로 봄 */
    public PhilosophyDetailResponse getDetail(Long userId, Long articleId) {
        PhilosophyArticle article = findPublished(articleId);
        String memo = memoRepository.findByUser_IdAndArticle_Id(userId, articleId)
                .map(PhilosophyMemo::getContent)
                .orElse(null);
        boolean bookmarked = bookmarkRepository.existsByUser_IdAndArticle_Id(userId, articleId);
        return PhilosophyDetailResponse.of(article, memo, bookmarked);
    }

    /** 사용자가 볼 수 있는 글. 아니면 {@code ARTICLE_NOT_FOUND} (승인 전 · 공개 전 글이 있는지도 드러내지 않음) */
    PhilosophyArticle findPublished(Long articleId) {
        return articleRepository.findById(articleId)
                .filter(article -> article.isPublished(LocalDate.now(clock)))
                .orElseThrow(() -> new BusinessException(PhilosophyErrorCode.ARTICLE_NOT_FOUND));
    }

    /** 카드 목록. 철학자 이름 · 북마크 여부는 글마다 따로 읽지 않고 한 번에 읽음 */
    List<PhilosophyCardResponse> toCards(Long userId, List<PhilosophyArticle> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> ids = articles.stream().map(PhilosophyArticle::getId).toList();
        Map<Long, List<String>> names = new HashMap<>();
        for (ArticlePhilosopherName name : articleRepository.findPhilosopherNames(ids)) {
            names.computeIfAbsent(name.articleId(), id -> new ArrayList<>()).add(name.philosopherName());
        }
        Set<Long> bookmarked = new HashSet<>(bookmarkRepository.findBookmarkedArticleIds(userId, ids));
        return articles.stream()
                .map(article -> PhilosophyCardResponse.of(article,
                        names.getOrDefault(article.getId(), List.of()), bookmarked.contains(article.getId())))
                .toList();
    }

    private LocalDate currentWeek() {
        return articleRepository.findLatestWeekStart(PhilosophyStatus.APPROVED, LocalDate.now(clock));
    }
}
