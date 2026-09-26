package likelion.yacha_backend.domain.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import likelion.yacha_backend.global.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 토론 세션 = 방 (API 명세 1-2 · 2-3).
 *
 * <p>대기 중인 방({@link SessionStatus#WAITING})이 곧 매칭 대기열입니다. 입장 · AI 전환 · 취소처럼
 * 여러 요청이 같은 방을 동시에 바꿀 수 있는 전환은 엔티티 메서드가 아니라
 * {@code DebateSessionRepository} 의 조건부 UPDATE({@code status = WAITING}) 로 합니다.
 * 1건이 갱신된 쪽만 성공하므로 두 사람이 동시에 들어와도 한 명만 들어갑니다.
 *
 * <p>채팅 · 근거 · 요약 · 판정 상세는 이 테이블에 없습니다. 게임 동안 서버 메모리에만 둡니다 (명세 1-5).
 */
@Entity
@Table(
        name = "debate_session",
        indexes = {
                // 대기열 · 방 찾기 · 관전 목록을 오래된(또는 최근) 순서로 읽는다
                @Index(name = "idx_debate_session_queue", columnList = "room_type, status, category, created_at"),
                @Index(name = "idx_debate_session_topic", columnList = "topic_id, created_at"),
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_debate_session_invite_code", columnNames = "invite_code")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebateSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * → topic. 랜덤 방은 생성할 때 채우고, 친구 방은 친구가 들어올 때까지 NULL 입니다.
     * topic 엔티티가 생기면 연관관계로 바꿉니다.
     */
    @Column(name = "topic_id")
    private Long topicId;

    /** 방을 만들 때 고른 카테고리. 카테고리 8개의 이름이 정해지면 enum 으로 바꿉니다. */
    @Column(nullable = false, length = 20)
    private String category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    /** 친구 방 초대 코드. 랜덤 방은 NULL. 만료는 {@code created_at + 10분} 으로 계산합니다. */
    @Column(name = "invite_code", length = 32)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SessionMode mode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "evidence_mode", nullable = false, length = 20)
    private EvidenceMode evidenceMode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    /** 매칭 성사 시각. 현재 구간은 {@link DebatePhase#at} 으로 이 값에서 계산합니다. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "finish_reason", length = 20)
    private FinishReason finishReason;

    /** 도전장용. 컬럼만 확보해 둡니다. */
    @Column(name = "origin_session_id")
    private Long originSessionId;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** 랜덤 야차 방. 방장이 받은 주제로 만들고 상대를 기다립니다. */
    public static DebateSession createRandom(String category, Long topicId) {
        DebateSession session = waiting(category, RoomType.RANDOM);
        session.topicId = Objects.requireNonNull(topicId, "topicId");
        return session;
    }

    /** 친구와 야차 방. 주제는 친구가 들어올 때 서버가 정합니다. */
    public static DebateSession createFriend(String category, String inviteCode) {
        DebateSession session = waiting(category, RoomType.FRIEND);
        session.inviteCode = Objects.requireNonNull(inviteCode, "inviteCode");
        return session;
    }

    /**
     * 자동 봇전. 제안을 모두 거절한 사용자에게 서버가 바로 만들어 주므로 대기 없이 시작합니다.
     * 대기열에 올라가지 않는 방이라 조건부 UPDATE 없이 여기서 시작 상태로 만듭니다.
     */
    public static DebateSession createAiMatch(String category, Long topicId, LocalDateTime now) {
        DebateSession session = waiting(category, RoomType.RANDOM);
        session.topicId = Objects.requireNonNull(topicId, "topicId");
        session.mode = SessionMode.AI;
        session.status = SessionStatus.IN_PROGRESS;
        session.startedAt = Objects.requireNonNull(now, "now");
        return session;
    }

    private static DebateSession waiting(String category, RoomType roomType) {
        DebateSession session = new DebateSession();
        session.category = Objects.requireNonNull(category, "category");
        session.roomType = roomType;
        session.mode = SessionMode.HUMAN;
        session.evidenceMode = EvidenceMode.ENABLED;
        session.status = SessionStatus.WAITING;
        return session;
    }

    /**
     * 진행 중인 게임을 끝냅니다. 종료는 시간표 스케줄러 · 이탈 처리 · 재시작 정리가 하나의 흐름에서만
     * 호출하므로 조건부 UPDATE 가 필요 없습니다.
     */
    public void finish(FinishReason reason, LocalDateTime now) {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 세션만 종료할 수 있습니다. sessionId=" + id + ", status=" + status);
        }
        this.status = SessionStatus.FINISHED;
        this.finishReason = Objects.requireNonNull(reason, "reason");
        this.endedAt = Objects.requireNonNull(now, "now");
    }

    public boolean isWaiting() {
        return status == SessionStatus.WAITING;
    }

    public boolean isInProgress() {
        return status == SessionStatus.IN_PROGRESS;
    }
}
