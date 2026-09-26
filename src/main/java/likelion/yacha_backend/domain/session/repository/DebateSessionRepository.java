package likelion.yacha_backend.domain.session.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.SessionMode;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 대기 중인 방의 상태 전환은 모두 {@code status = WAITING} 조건의 UPDATE 로 합니다 (명세 2-3-4).
 *
 * <p>반환값은 갱신된 행 수입니다. <b>1 이면 성공, 0 이면 이미 다른 요청이 먼저 바꾼 것</b>이라
 * 호출한 쪽은 {@code SESSION_NOT_WAITING} 으로 처리합니다. 팝업에서 AI 를 누르는 순간 사람이 들어와도
 * 둘 중 하나만 성공합니다.
 *
 * <p>벌크 UPDATE 는 영속성 컨텍스트를 거치지 않으므로 Auditing 이 {@code updated_at} 을 채우지 않습니다.
 * 그래서 쿼리에서 직접 넣고, 끝난 뒤 컨텍스트를 비워(clearAutomatically) 다음 조회가 DB 값을 읽게 합니다.
 */
public interface DebateSessionRepository extends JpaRepository<DebateSession, Long> {

    /** 랜덤 방에 상대가 들어와 시작합니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DebateSession s
               set s.status = :inProgress, s.startedAt = :now, s.updatedAt = :now
             where s.id = :id and s.status = :waiting
            """)
    int startIfWaiting(@Param("id") Long id, @Param("now") LocalDateTime now,
                       @Param("waiting") SessionStatus waiting, @Param("inProgress") SessionStatus inProgress);

    default int startIfWaiting(Long id, LocalDateTime now) {
        return startIfWaiting(id, now, SessionStatus.WAITING, SessionStatus.IN_PROGRESS);
    }

    /** 친구가 들어와 시작합니다. 주제도 이때 정해집니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DebateSession s
               set s.status = :inProgress, s.topicId = :topicId, s.startedAt = :now, s.updatedAt = :now
             where s.id = :id and s.status = :waiting
            """)
    int startFriendIfWaiting(@Param("id") Long id, @Param("topicId") Long topicId, @Param("now") LocalDateTime now,
                             @Param("waiting") SessionStatus waiting, @Param("inProgress") SessionStatus inProgress);

    default int startFriendIfWaiting(Long id, Long topicId, LocalDateTime now) {
        return startFriendIfWaiting(id, topicId, now, SessionStatus.WAITING, SessionStatus.IN_PROGRESS);
    }

    /** 방장이 대기 중에 "AI와 대결" 을 골랐습니다. 대기열에서 바로 빠지고 봇전으로 시작합니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DebateSession s
               set s.status = :inProgress, s.mode = :ai, s.startedAt = :now, s.updatedAt = :now
             where s.id = :id and s.status = :waiting
            """)
    int convertToAiIfWaiting(@Param("id") Long id, @Param("now") LocalDateTime now,
                             @Param("waiting") SessionStatus waiting, @Param("inProgress") SessionStatus inProgress,
                             @Param("ai") SessionMode ai);

    default int convertToAiIfWaiting(Long id, LocalDateTime now) {
        return convertToAiIfWaiting(id, now, SessionStatus.WAITING, SessionStatus.IN_PROGRESS, SessionMode.AI);
    }

    /** 대기 취소 · 5분 상한 · 초대 만료. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DebateSession s
               set s.status = :cancelled, s.endedAt = :now, s.updatedAt = :now
             where s.id = :id and s.status = :waiting
            """)
    int cancelIfWaiting(@Param("id") Long id, @Param("now") LocalDateTime now,
                        @Param("waiting") SessionStatus waiting, @Param("cancelled") SessionStatus cancelled);

    default int cancelIfWaiting(Long id, LocalDateTime now) {
        return cancelIfWaiting(id, now, SessionStatus.WAITING, SessionStatus.CANCELLED);
    }

    Optional<DebateSession> findByInviteCode(String inviteCode);

    /** 서버 재시작 정리용. 메모리에 있던 게임은 이어갈 수 없으므로 IN_PROGRESS 를 ABORTED 로 끝냅니다. */
    List<DebateSession> findAllByStatus(SessionStatus status);
}
