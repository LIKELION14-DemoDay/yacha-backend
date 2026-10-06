package likelion.yacha_backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.auth.repository.RefreshTokenStore;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;
import likelion.yacha_backend.domain.topic.repository.TopicRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄링은 테스트에서 꺼져 있어 {@link GuestCleanupJob#run()} 을 직접 부릅니다.
 * 오래된 계정은 {@code created_at} 을 SQL 로 과거로 돌려 만듭니다 (생성 시각은 Auditing 이 넣어서 직접 못 정함).
 * 묶음을 여러 번 도는지 보려고 묶음 크기를 2로 줄였습니다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "auth.guest-cleanup.batch-size=2")
@DisplayName("오래된 게스트 계정 정리")
class GuestCleanupJobTest {

    @Autowired
    private GuestCleanupJob guestCleanupJob;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 15일 전에 만들어진 게스트 (기준은 14일) */
    private Long oldGuest() {
        Long id = userRepository.save(User.createGuest("오래된게스트")).getId();
        backdate(id, 15);
        return id;
    }

    private void backdate(Long userId, int days) {
        userRepository.flush();
        jdbcTemplate.update("update users set created_at = ? where id = ?",
                LocalDateTime.now().minusDays(days), userId);
    }

    @Test
    @DisplayName("오래됐고 리프레시 토큰이 없는 게스트는 지워진다")
    void deletesStaleGuest() {
        Long guestId = oldGuest();

        guestCleanupJob.run();

        assertThat(userRepository.existsById(guestId)).isFalse();
    }

    @Test
    @DisplayName("참가 기록이 있어도 지워지고, 참가 기록은 남은 채 사용자 연결만 끊긴다 (FK)")
    void detachesParticipantsBeforeDelete() {
        Long guestId = oldGuest();
        User member = userRepository.save(User.createMember("host@example.com", "encoded", "방장"));
        DebateSession session = sessionRepository.save(DebateSession.createRandom(newTopic()));
        LocalDateTime now = LocalDateTime.now();
        participantRepository.save(DebateParticipant.initiator(session, member, Stance.AGREE, now));
        Long guestParticipantId = participantRepository.save(DebateParticipant.opponent(
                session, userRepository.getReferenceById(guestId), Stance.DISAGREE, now)).getId();

        guestCleanupJob.run();

        assertThat(userRepository.existsById(guestId)).isFalse();
        assertThat(participantRepository.findById(guestParticipantId)).get()
                .extracting(DebateParticipant::getUser).isNull();
        // 상대(회원)의 참가 기록은 그대로
        assertThat(participantRepository.existsBySession_IdAndUser_Id(session.getId(), member.getId())).isTrue();
    }

    @Test
    @DisplayName("리프레시 토큰이 남아 있으면 아직 쓰는 계정이라 지우지 않는다")
    void keepsGuestWithRefreshToken() {
        Long guestId = oldGuest();
        refreshTokenStore.save(guestId, "still-in-use");

        guestCleanupJob.run();

        assertThat(userRepository.existsById(guestId)).isTrue();
        refreshTokenStore.delete(guestId);
    }

    @Test
    @DisplayName("만든 지 14일이 안 된 게스트는 지우지 않는다")
    void keepsRecentGuest() {
        Long guestId = userRepository.save(User.createGuest("새게스트")).getId();
        backdate(guestId, 13);

        guestCleanupJob.run();

        assertThat(userRepository.existsById(guestId)).isTrue();
    }

    @Test
    @DisplayName("회원은 오래됐고 토큰이 없어도 지우지 않는다")
    void keepsMembers() {
        Long memberId = userRepository.save(User.createMember("old@example.com", "encoded", "회원")).getId();
        backdate(memberId, 30);

        guestCleanupJob.run();

        assertThat(userRepository.existsById(memberId)).isTrue();
    }

    @Test
    @DisplayName("지우기 직전 다시 확인할 때 이미 회원이 된 계정은 빠진다 (정리 중 승격)")
    void recheckSkipsUpgradedUser() {
        Long stillGuest = oldGuest();
        User upgraded = userRepository.save(User.createGuest("승격할게스트"));
        backdate(upgraded.getId(), 15);
        // 후보로 뽑힌 뒤 지우기 전에 승격된 상황
        upgraded.upgradeToMember("upgraded@example.com", "encoded", "승격");
        userRepository.flush();

        List<User> deletable = userRepository.findGuestsByIdForUpdate(List.of(stillGuest, upgraded.getId()));

        assertThat(deletable).extracting(User::getId).containsExactly(stillGuest);
    }

    @Test
    @DisplayName("묶음 크기보다 많아도, 중간에 남길 계정이 섞여 있어도 끝까지 정리한다")
    void processesAllBatches() {
        Long first = oldGuest();
        Long kept = oldGuest();
        refreshTokenStore.save(kept, "still-in-use");
        Long third = oldGuest();
        Long fourth = oldGuest();
        Long fifth = oldGuest();

        int deleted = guestCleanupJob.run();

        assertThat(deleted).isEqualTo(4);
        assertThat(userRepository.findAllById(List.of(first, third, fourth, fifth))).isEmpty();
        assertThat(userRepository.existsById(kept)).isTrue();
        refreshTokenStore.delete(kept);
    }

    private Topic newTopic() {
        return topicRepository.save(Topic.create(Subcategory.GOOD_AND_EVIL, "질문", "찬성", "반대"));
    }
}
