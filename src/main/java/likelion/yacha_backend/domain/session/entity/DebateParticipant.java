package likelion.yacha_backend.domain.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import likelion.yacha_backend.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 토론 참가자 (API 명세 1-2). 한 세션에 사람 2명, 또는 사람 1명 + AI 1명.
 *
 * <p>관전자는 참가자가 아닙니다. 구독만 하고 이 테이블에 행이 생기지 않습니다.
 */
@Entity
@Table(
        name = "debate_participant",
        indexes = {
                // 전투 기록 · 이미 참여 중인 세션 확인
                @Index(name = "idx_debate_participant_user", columnList = "user_id"),
                @Index(name = "idx_debate_participant_session", columnList = "session_id"),
        },
        // 같은 사람이 한 세션에 두 번 들어가지 않게 합니다. AI 는 user_id 가 NULL 이라 걸리지 않습니다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_debate_participant_session_user", columnNames = {"session_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebateParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private DebateSession session;

    /** AI 참가자는 NULL. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "participant_type", nullable = false, length = 20)
    private ParticipantType participantType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ParticipantRole role;

    /**
     * 동의 / 비동의. 친구 방의 방장은 친구가 들어올 때 서버가 무작위로 정하므로 그 전까지 NULL 입니다.
     * 배정 규칙은 명세 1-2 의 {@code stance} 배정 규칙 표를 따릅니다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private Stance stance;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /** 연결이 끊긴 시각. 재접속하면 NULL 로 되돌립니다 (이탈 유예 판단용). */
    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    /** 승패. 판정 전이거나 게임이 무효(ABORTED)면 NULL. DB 에 남는 게임 결과는 이것뿐입니다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private DebateResult result;

    /** 방장. 친구 방이면 {@code stance} 를 null 로 넘깁니다. */
    public static DebateParticipant initiator(DebateSession session, User user, Stance stance, LocalDateTime now) {
        return human(session, user, ParticipantRole.INITIATOR, stance, now);
    }

    /** 방에 들어온 사람. */
    public static DebateParticipant opponent(DebateSession session, User user, Stance stance, LocalDateTime now) {
        return human(session, user, ParticipantRole.OPPONENT, Objects.requireNonNull(stance, "stance"), now);
    }

    /** 봇전의 AI 상대. 입장은 사용자의 반대입니다. */
    public static DebateParticipant ai(DebateSession session, Stance stance, LocalDateTime now) {
        DebateParticipant participant = new DebateParticipant();
        participant.session = Objects.requireNonNull(session, "session");
        participant.participantType = ParticipantType.AI;
        participant.role = ParticipantRole.OPPONENT;
        participant.stance = Objects.requireNonNull(stance, "stance");
        participant.joinedAt = Objects.requireNonNull(now, "now");
        return participant;
    }

    private static DebateParticipant human(DebateSession session, User user, ParticipantRole role,
                                           Stance stance, LocalDateTime now) {
        DebateParticipant participant = new DebateParticipant();
        participant.session = Objects.requireNonNull(session, "session");
        participant.user = Objects.requireNonNull(user, "user");
        participant.participantType = ParticipantType.USER;
        participant.role = role;
        participant.stance = stance;
        participant.joinedAt = Objects.requireNonNull(now, "now");
        return participant;
    }

    /** 친구 방에서 친구가 들어올 때 방장의 입장을 정합니다. 한 번만 정할 수 있습니다. */
    public void assignStance(Stance stance) {
        if (this.stance != null) {
            throw new IllegalStateException("입장이 이미 정해진 참가자입니다. participantId=" + id);
        }
        this.stance = Objects.requireNonNull(stance, "stance");
    }

    public void markDisconnected(LocalDateTime now) {
        this.disconnectedAt = Objects.requireNonNull(now, "now");
    }

    public void markReconnected() {
        this.disconnectedAt = null;
    }

    /** 판정 결과를 기록합니다. 한 번만 기록할 수 있습니다. */
    public void recordResult(DebateResult result) {
        if (this.result != null) {
            throw new IllegalStateException("결과가 이미 기록된 참가자입니다. participantId=" + id);
        }
        this.result = Objects.requireNonNull(result, "result");
    }

    public boolean isAi() {
        return participantType == ParticipantType.AI;
    }
}
