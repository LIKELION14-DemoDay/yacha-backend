package likelion.yacha_backend.domain.session.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import likelion.yacha_backend.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DebateSession · DebateParticipant — 엔티티 규칙")
class DebateSessionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0, 0);

    @Test
    @DisplayName("랜덤 방은 주제를 가진 채 사람 대 사람 대기 상태로 만들어진다")
    void createsRandomRoom() {
        DebateSession session = DebateSession.createRandom("ETHICS", 12L);

        assertThat(session.getRoomType()).isEqualTo(RoomType.RANDOM);
        assertThat(session.getMode()).isEqualTo(SessionMode.HUMAN);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.WAITING);
        assertThat(session.getTopicId()).isEqualTo(12L);
        assertThat(session.getInviteCode()).isNull();
        assertThat(session.getStartedAt()).isNull();
    }

    @Test
    @DisplayName("친구 방은 주제 없이 초대 코드를 가진 채 만들어진다")
    void createsFriendRoom() {
        DebateSession session = DebateSession.createFriend("LOVE", "k3Xp9aQ2");

        assertThat(session.getRoomType()).isEqualTo(RoomType.FRIEND);
        assertThat(session.getTopicId()).isNull();
        assertThat(session.getInviteCode()).isEqualTo("k3Xp9aQ2");
        assertThat(session.isWaiting()).isTrue();
    }

    @Test
    @DisplayName("자동 봇전은 대기 없이 바로 시작한다")
    void aiMatchStartsImmediately() {
        DebateSession session = DebateSession.createAiMatch("ETHICS", 12L, NOW);

        assertThat(session.getMode()).isEqualTo(SessionMode.AI);
        assertThat(session.isInProgress()).isTrue();
        assertThat(session.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("진행 중인 세션만 종료할 수 있다")
    void finishesOnlyInProgress() {
        DebateSession inProgress = DebateSession.createAiMatch("ETHICS", 12L, NOW);
        inProgress.finish(FinishReason.ABORTED, NOW.plusMinutes(1));

        assertThat(inProgress.getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(inProgress.getFinishReason()).isEqualTo(FinishReason.ABORTED);
        assertThat(inProgress.getEndedAt()).isEqualTo(NOW.plusMinutes(1));

        DebateSession waiting = DebateSession.createRandom("ETHICS", 12L);
        assertThatThrownBy(() -> waiting.finish(FinishReason.COMPLETED, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("입장은 서로 반대다")
    void stanceOpposite() {
        assertThat(Stance.AGREE.opposite()).isEqualTo(Stance.DISAGREE);
        assertThat(Stance.DISAGREE.opposite()).isEqualTo(Stance.AGREE);
    }

    @Test
    @DisplayName("친구 방 방장의 입장은 한 번만 정할 수 있다")
    void assignsStanceOnce() {
        DebateParticipant host = DebateParticipant.initiator(
                DebateSession.createFriend("LOVE", "code"), User.createGuest("방장"), null, NOW);

        host.assignStance(Stance.DISAGREE);

        assertThat(host.getStance()).isEqualTo(Stance.DISAGREE);
        assertThatThrownBy(() -> host.assignStance(Stance.AGREE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("승패는 한 번만 기록할 수 있다")
    void recordsResultOnce() {
        DebateParticipant participant = DebateParticipant.initiator(
                DebateSession.createRandom("ETHICS", 12L), User.createGuest("방장"), Stance.AGREE, NOW);

        participant.recordResult(DebateResult.WIN);

        assertThat(participant.getResult()).isEqualTo(DebateResult.WIN);
        assertThatThrownBy(() -> participant.recordResult(DebateResult.LOSE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("끊기면 시각을 기록하고, 재접속하면 지운다")
    void tracksDisconnection() {
        DebateParticipant participant = DebateParticipant.initiator(
                DebateSession.createRandom("ETHICS", 12L), User.createGuest("방장"), Stance.AGREE, NOW);

        participant.markDisconnected(NOW);
        assertThat(participant.getDisconnectedAt()).isEqualTo(NOW);

        participant.markReconnected();
        assertThat(participant.getDisconnectedAt()).isNull();
    }

    @Test
    @DisplayName("AI 참가자는 사용자가 없고 상대 역할이다")
    void aiParticipant() {
        DebateParticipant ai = DebateParticipant.ai(DebateSession.createAiMatch("ETHICS", 12L, NOW), Stance.AGREE, NOW);

        assertThat(ai.isAi()).isTrue();
        assertThat(ai.getUser()).isNull();
        assertThat(ai.getRole()).isEqualTo(ParticipantRole.OPPONENT);
    }
}
