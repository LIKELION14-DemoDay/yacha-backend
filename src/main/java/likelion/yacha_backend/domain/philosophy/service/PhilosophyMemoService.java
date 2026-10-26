package likelion.yacha_backend.domain.philosophy.service;

import java.util.Optional;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyMemoResponse;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyMemo;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyMemoRepository;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 1분 철학 메모. 한 사람이 글마다 하나, 다시 저장하면 덮어씀
 * 공개된 글에만 쓸 수 있음
 */
@Service
public class PhilosophyMemoService {

    private final PhilosophyService philosophyService;
    private final PhilosophyMemoRepository memoRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public PhilosophyMemoService(PhilosophyService philosophyService, PhilosophyMemoRepository memoRepository,
                                 UserRepository userRepository, PlatformTransactionManager transactionManager) {
        this.philosophyService = philosophyService;
        this.memoRepository = memoRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 메모 저장. 앞뒤 공백을 지우고, 비어 있으면 메모를 지움
     *
     * 처음 저장을 두 번 연달아 보내면 둘 다 "메모 없음"을 보고 만들려다 한쪽이 유일 제약에 걸림
     * 그때는 새 트랜잭션으로 한 번 더 해서, 먼저 만들어진 메모를 덮어씀
     */
    public PhilosophyMemoResponse save(Long userId, Long articleId, String content) {
        String memo = content.strip();
        try {
            return transactionTemplate.execute(status -> saveOnce(userId, articleId, memo));
        } catch (DataIntegrityViolationException e) {
            return transactionTemplate.execute(status -> saveOnce(userId, articleId, memo));
        }
    }

    private PhilosophyMemoResponse saveOnce(Long userId, Long articleId, String memo) {
        PhilosophyArticle article = philosophyService.findPublished(articleId);
        Optional<PhilosophyMemo> existing = memoRepository.findByUser_IdAndArticle_Id(userId, articleId);
        if (memo.isEmpty()) {
            existing.ifPresent(memoRepository::delete);
            return new PhilosophyMemoResponse(null);
        }
        existing.ifPresentOrElse(
                found -> found.updateContent(memo),
                () -> memoRepository.saveAndFlush(
                        PhilosophyMemo.create(userRepository.getReferenceById(userId), article, memo)));
        return new PhilosophyMemoResponse(memo);
    }
}
