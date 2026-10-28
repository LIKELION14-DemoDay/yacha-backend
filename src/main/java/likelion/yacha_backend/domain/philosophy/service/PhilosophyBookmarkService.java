package likelion.yacha_backend.domain.philosophy.service;

import java.time.Clock;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyBookmarkResponse;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyCardsResponse;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyBookmark;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyBookmarkRepository;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 1분 철학 북마크. 마이페이지에서 북마크한 순서(최신순)로 모아 봄
 * 추가 · 해제는 여러 번 불러도 결과가 같음. 공개된 글에만 북마크할 수 있음
 *
 * 공개된 글은 다시 생성 · 빼기가 막혀 있어서, 북마크한 글이 사라지거나 안 보이게 되는 일은 없음
 */
@Service
public class PhilosophyBookmarkService {

    private final PhilosophyService philosophyService;
    private final PhilosophyBookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PhilosophyBookmarkService(PhilosophyService philosophyService,
                                     PhilosophyBookmarkRepository bookmarkRepository, UserRepository userRepository,
                                     PlatformTransactionManager transactionManager, Clock clock) {
        this.philosophyService = philosophyService;
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 북마크 추가. 이미 돼 있으면 그대로
     * 두 번 연달아 보내 한쪽이 유일 제약에 걸리면, 새 트랜잭션으로 한 번 더 해서 이미 있는 것으로 끝냄
     */
    public PhilosophyBookmarkResponse add(Long userId, Long articleId) {
        try {
            transactionTemplate.executeWithoutResult(status -> addOnce(userId, articleId));
        } catch (DataIntegrityViolationException e) {
            transactionTemplate.executeWithoutResult(status -> addOnce(userId, articleId));
        }
        return new PhilosophyBookmarkResponse(true);
    }

    private void addOnce(Long userId, Long articleId) {
        PhilosophyArticle article = philosophyService.findPublished(articleId);
        if (!bookmarkRepository.existsByUser_IdAndArticle_Id(userId, articleId)) {
            bookmarkRepository.saveAndFlush(PhilosophyBookmark.create(
                    userRepository.getReferenceById(userId), article, LocalDateTime.now(clock)));
        }
    }

    /** 북마크 해제. 안 돼 있으면 그대로 */
    public PhilosophyBookmarkResponse remove(Long userId, Long articleId) {
        transactionTemplate.executeWithoutResult(status -> {
            philosophyService.findPublished(articleId);
            bookmarkRepository.findByUser_IdAndArticle_Id(userId, articleId).ifPresent(bookmarkRepository::delete);
        });
        return new PhilosophyBookmarkResponse(false);
    }

    /** 내 북마크 카드, 북마크한 순서 최신부터 한 페이지 {@value PhilosophyService#PAGE_SIZE}개 ({@code page}는 0부터) */
    public PhilosophyCardsResponse getMine(Long userId, int page) {
        if (page < 0) {
            throw new BusinessException(GlobalErrorCode.VALIDATION_FAILED);
        }
        return transactionTemplate.execute(status -> {
            Slice<PhilosophyBookmark> bookmarks = bookmarkRepository.findMine(
                    userId, PageRequest.of(page, PhilosophyService.PAGE_SIZE));
            return new PhilosophyCardsResponse(
                    philosophyService.toCards(userId, bookmarks.map(PhilosophyBookmark::getArticle).getContent()),
                    bookmarks.hasNext());
        });
    }
}
