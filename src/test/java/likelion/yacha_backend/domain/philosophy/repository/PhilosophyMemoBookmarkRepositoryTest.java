package likelion.yacha_backend.domain.philosophy.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyBookmark;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyMemo;
import likelion.yacha_backend.domain.philosophy.entity.WeeklyPhilosophy;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@DisplayName("1분 철학 메모 · 북마크 저장")
class PhilosophyMemoBookmarkRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);

    @Autowired
    private PhilosophyMemoRepository memoRepository;

    @Autowired
    private PhilosophyBookmarkRepository bookmarkRepository;

    @Autowired
    private WeeklyPhilosophyRepository weeklyRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    private User member;
    private WeeklyPhilosophy weekly;

    @BeforeEach
    void setUp() {
        member = userRepository.save(User.createMember("member@test.com", "encoded", "회원"));
        Topic topic = topicRepository.save(Topic.create(Subcategory.GOOD_AND_EVIL, "질문", "찬성", "반대"));
        weekly = weeklyRepository.save(WeeklyPhilosophy.createPending(topic, LocalDate.of(2026, 11, 2)));
    }

    @Test
    @DisplayName("메모는 한 사람이 주제마다 하나만 가진다")
    void oneMemoPerUserAndWeekly() {
        memoRepository.saveAndFlush(PhilosophyMemo.create(member, weekly, "첫 메모"));

        assertThatThrownBy(() -> memoRepository.saveAndFlush(PhilosophyMemo.create(member, weekly, "둘째 메모")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 주제를 두 번 북마크할 수 없다")
    void oneBookmarkPerUserAndWeekly() {
        bookmarkRepository.saveAndFlush(PhilosophyBookmark.create(member, weekly, NOW));

        assertThatThrownBy(() -> bookmarkRepository.saveAndFlush(PhilosophyBookmark.create(member, weekly, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
