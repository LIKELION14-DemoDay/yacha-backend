package likelion.yacha_backend.domain.session.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.SessionMode;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@DisplayName("DebateSessionRepository")
class DebateSessionRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0, 0);

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Nested
    @DisplayName("대기 방 상태 전환 — status = WAITING 조건의 UPDATE")
    class WaitingTransitions {

        @Test
        @DisplayName("상대가 들어오면 IN_PROGRESS 로 바뀌고 시작 시각이 기록된다")
        void startsWaitingRoom() {
            Long id = sessionRepository.save(DebateSession.createRandom("ETHICS", 12L)).getId();

            int updated = sessionRepository.startIfWaiting(id, NOW);

            DebateSession session = sessionRepository.findById(id).orElseThrow();
            assertThat(updated).isEqualTo(1);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(session.getStartedAt()).isEqualTo(NOW);
            assertThat(session.getUpdatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("두 사람이 같은 방에 들어오면 먼저 들어온 쪽만 성공한다")
        void onlyFirstJoinWins() {
            Long id = sessionRepository.save(DebateSession.createRandom("ETHICS", 12L)).getId();

            int first = sessionRepository.startIfWaiting(id, NOW);
            int second = sessionRepository.startIfWaiting(id, NOW.plusSeconds(1));

            assertThat(first).isEqualTo(1);
            assertThat(second).isZero();
            assertThat(sessionRepository.findById(id).orElseThrow().getStartedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("사람이 먼저 들어오면 방장의 AI 전환은 실패하고 사람 대 사람으로 남는다")
        void joinBeatsAiConversion() {
            Long id = sessionRepository.save(DebateSession.createRandom("ETHICS", 12L)).getId();

            sessionRepository.startIfWaiting(id, NOW);
            int converted = sessionRepository.convertToAiIfWaiting(id, NOW.plusSeconds(1));

            assertThat(converted).isZero();
            assertThat(sessionRepository.findById(id).orElseThrow().getMode()).isEqualTo(SessionMode.HUMAN);
        }

        @Test
        @DisplayName("AI 로 전환하면 봇전으로 시작한다")
        void convertsToAi() {
            Long id = sessionRepository.save(DebateSession.createRandom("ETHICS", 12L)).getId();

            int converted = sessionRepository.convertToAiIfWaiting(id, NOW);

            DebateSession session = sessionRepository.findById(id).orElseThrow();
            assertThat(converted).isEqualTo(1);
            assertThat(session.getMode()).isEqualTo(SessionMode.AI);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(session.getStartedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("친구가 들어오면 주제가 정해지고 시작한다")
        void startsFriendRoomWithTopic() {
            Long id = sessionRepository.save(DebateSession.createFriend("LOVE", "k3Xp9aQ2")).getId();
            assertThat(sessionRepository.findById(id).orElseThrow().getTopicId()).isNull();

            int updated = sessionRepository.startFriendIfWaiting(id, 30L, NOW);

            DebateSession session = sessionRepository.findById(id).orElseThrow();
            assertThat(updated).isEqualTo(1);
            assertThat(session.getTopicId()).isEqualTo(30L);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("취소된 방에는 들어갈 수 없다")
        void cannotJoinCancelledRoom() {
            Long id = sessionRepository.save(DebateSession.createRandom("ETHICS", 12L)).getId();

            int cancelled = sessionRepository.cancelIfWaiting(id, NOW);
            int joined = sessionRepository.startIfWaiting(id, NOW.plusSeconds(1));

            DebateSession session = sessionRepository.findById(id).orElseThrow();
            assertThat(cancelled).isEqualTo(1);
            assertThat(joined).isZero();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
            assertThat(session.getEndedAt()).isEqualTo(NOW);
        }
    }

    @Test
    @DisplayName("초대 코드는 중복될 수 없다")
    void inviteCodeIsUnique() {
        sessionRepository.saveAndFlush(DebateSession.createFriend("LOVE", "dup-code"));

        assertThatThrownBy(() -> sessionRepository.saveAndFlush(DebateSession.createFriend("ETHICS", "dup-code")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("초대 코드로 방을 찾는다")
    void findsByInviteCode() {
        Long id = sessionRepository.save(DebateSession.createFriend("LOVE", "k3Xp9aQ2")).getId();

        assertThat(sessionRepository.findByInviteCode("k3Xp9aQ2")).map(DebateSession::getId).contains(id);
        assertThat(sessionRepository.findByInviteCode("unknown")).isEmpty();
    }

    @Test
    @DisplayName("상태별로 세션을 찾는다 (재시작 정리용)")
    void findsAllByStatus() {
        DebateSession waiting = sessionRepository.save(DebateSession.createRandom("ETHICS", 12L));
        DebateSession aiMatch = sessionRepository.save(DebateSession.createAiMatch("ETHICS", 13L, NOW));

        assertThat(sessionRepository.findAllByStatus(SessionStatus.IN_PROGRESS))
                .extracting(DebateSession::getId)
                .containsExactly(aiMatch.getId())
                .doesNotContain(waiting.getId());
    }

    @Test
    @DisplayName("enum 은 이름(문자열)으로 저장된다")
    void storesEnumsAsStrings() {
        Long id = sessionRepository.saveAndFlush(DebateSession.createRandom("ETHICS", 12L)).getId();

        Object[] row = (Object[]) entityManager
                .createNativeQuery("select room_type, mode, status, evidence_mode from debate_session where id = :id")
                .setParameter("id", id)
                .getSingleResult();

        assertThat(row).containsExactly("RANDOM", "HUMAN", "WAITING", "ENABLED");
    }
}
