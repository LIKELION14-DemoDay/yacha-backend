package likelion.yacha_backend.domain.philosophy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.util.Optional;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyBookmarkResponse;
import likelion.yacha_backend.domain.philosophy.dto.PhilosophyMemoResponse;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyMemo;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyBookmarkRepository;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyMemoRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/** 처음 저장을 두 번 연달아 보내 한쪽이 유일 제약에 걸렸을 때 */
@DisplayName("1분 철학 메모 · 북마크 — 동시 저장 충돌")
class PhilosophyConflictRetryTest {

    private static final Long USER_ID = 1L;
    private static final Long ARTICLE_ID = 10L;

    private final PhilosophyService philosophyService = mock(PhilosophyService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PhilosophyArticle article = mock(PhilosophyArticle.class);

    {
        given(philosophyService.findPublished(ARTICLE_ID)).willReturn(article);
        given(userRepository.getReferenceById(USER_ID)).willReturn(mock(User.class));
    }

    @Test
    @DisplayName("메모: 먼저 만들어진 메모를 다시 읽어 덮어쓴다")
    void memoOverwritesWinner() {
        PhilosophyMemoRepository memoRepository = mock(PhilosophyMemoRepository.class);
        PhilosophyMemo winner = PhilosophyMemo.create(mock(User.class), article, "먼저 저장된 메모");
        given(memoRepository.findByUser_IdAndArticle_Id(USER_ID, ARTICLE_ID))
                .willReturn(Optional.empty(), Optional.of(winner));
        given(memoRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk"));
        PhilosophyMemoService service =
                new PhilosophyMemoService(philosophyService, memoRepository, userRepository, transactionManager);

        PhilosophyMemoResponse response = service.save(USER_ID, ARTICLE_ID, "내 메모");

        assertThat(response.memo()).isEqualTo("내 메모");
        assertThat(winner.getContent()).isEqualTo("내 메모");
        verify(memoRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("북마크: 이미 있는 것으로 보고 성공으로 끝낸다")
    void bookmarkTreatsAsExisting() {
        PhilosophyBookmarkRepository bookmarkRepository = mock(PhilosophyBookmarkRepository.class);
        given(bookmarkRepository.existsByUser_IdAndArticle_Id(USER_ID, ARTICLE_ID)).willReturn(false, true);
        given(bookmarkRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk"));
        PhilosophyBookmarkService service = new PhilosophyBookmarkService(philosophyService, bookmarkRepository,
                userRepository, transactionManager, Clock.systemDefaultZone());

        PhilosophyBookmarkResponse response = service.add(USER_ID, ARTICLE_ID);

        assertThat(response.bookmarked()).isTrue();
        verify(bookmarkRepository, times(1)).saveAndFlush(any());
    }
}
