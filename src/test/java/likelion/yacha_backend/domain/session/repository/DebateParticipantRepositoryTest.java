package likelion.yacha_backend.domain.session.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.entity.Stance;
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
@DisplayName("DebateParticipantRepository")
class DebateParticipantRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0, 0);
    private static final List<SessionStatus> ACTIVE = List.of(SessionStatus.WAITING, SessionStatus.IN_PROGRESS);

    @Autowired
    private DebateParticipantRepository participantRepository;

    @Autowired
    private DebateSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    private User host;
    private User guest;
    private DebateSession session;

    @BeforeEach
    void setUp() {
        host = userRepository.save(User.createGuest("방장"));
        guest = userRepository.save(User.createGuest("상대"));
        session = sessionRepository.save(DebateSession.createRandom("ETHICS", 12L));
    }

    @Test
    @DisplayName("참가자인지 세션 id 와 사용자 id 로 확인한다")
    void checksParticipant() {
        participantRepository.save(DebateParticipant.initiator(session, host, Stance.AGREE, NOW));

        assertThat(participantRepository.existsBySession_IdAndUser_Id(session.getId(), host.getId())).isTrue();
        assertThat(participantRepository.existsBySession_IdAndUser_Id(session.getId(), guest.getId())).isFalse();
    }

    @Test
    @DisplayName("같은 사람은 한 세션에 두 번 들어갈 수 없다")
    void sameUserCannotJoinTwice() {
        participantRepository.saveAndFlush(DebateParticipant.initiator(session, host, Stance.AGREE, NOW));

        assertThatThrownBy(() -> participantRepository.saveAndFlush(
                DebateParticipant.opponent(session, host, Stance.DISAGREE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("AI 참가자는 사용자 없이 저장된다")
    void savesAiWithoutUser() {
        participantRepository.saveAndFlush(DebateParticipant.initiator(session, host, Stance.AGREE, NOW));
        participantRepository.saveAndFlush(DebateParticipant.ai(session, Stance.DISAGREE, NOW));

        assertThat(participantRepository.findAllBySession_Id(session.getId()))
                .extracting(DebateParticipant::isAi)
                .containsExactlyInAnyOrder(false, true);
    }

    @Test
    @DisplayName("대기 중이거나 진행 중인 세션이 있으면 참여 중으로 본다")
    void detectsActiveSession() {
        participantRepository.save(DebateParticipant.initiator(session, host, Stance.AGREE, NOW));

        assertThat(participantRepository.existsByUser_IdAndSession_StatusIn(host.getId(), ACTIVE)).isTrue();
        assertThat(participantRepository.existsByUser_IdAndSession_StatusIn(guest.getId(), ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("끝난 세션만 있으면 참여 중이 아니다")
    void finishedSessionIsNotActive() {
        DebateSession finished = sessionRepository.save(DebateSession.createAiMatch("ETHICS", 13L, NOW));
        finished.finish(FinishReason.COMPLETED, NOW.plusMinutes(9));
        participantRepository.saveAndFlush(DebateParticipant.initiator(finished, guest, Stance.AGREE, NOW));

        assertThat(participantRepository.existsByUser_IdAndSession_StatusIn(guest.getId(), ACTIVE)).isFalse();
    }
}
