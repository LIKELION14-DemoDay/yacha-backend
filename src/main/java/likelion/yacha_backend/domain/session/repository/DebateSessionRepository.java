package likelion.yacha_backend.domain.session.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.SessionMode;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.topic.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 세션의 상태 전환은 모두 현재 상태를 조건으로 건 UPDATE 로 합니다 (명세 2-3-4).
 * 대기 방의 입장 · AI 전환 · 취소는 {@code status = WAITING}, 게임 종료는 {@code status = IN_PROGRESS} 조건입니다.
 *
 * <p>반환값은 갱신된 행 수입니다. <b>1 이면 성공, 0 이면 이미 다른 요청이 먼저 바꾼 것</b>입니다.
 * UPDATE 는 행 락을 잡고 조건을 다시 확인하므로 동시에 와도 한쪽만 1 을 받습니다.
 * 팝업에서 AI 를 누르는 순간 사람이 들어와도, 시간 종료와 이탈이 겹쳐도 둘 중 하나만 성공합니다.
 *
 * <p>벌크 UPDATE 는 영속성 컨텍스트를 거치지 않으므로 Auditing 이 {@code updated_at} 을 채우지 않습니다.
 * 그래서 쿼리에서 직접 넣고, 끝난 뒤 컨텍스트를 비워(clearAutomatically) 다음 조회가 DB 값을 읽게 합니다.
 *
 * <p><b>주의:</b> 컨텍스트가 비워지므로 UPDATE 전에 조회한 {@code DebateSession} 은 준영속이 됩니다.
 * FK 로 넘기는 것은 괜찮지만, 그 인스턴스를 수정해도 DB 에 반영되지 않고 필드도 UPDATE 이전 값입니다.
 * UPDATE 뒤에 세션 값이 필요하면 {@code findById} 로 다시 조회하고, FK 로만 쓸 때는 {@code getReferenceById} 를 씁니다.
 * <pre>{@code
 * if (sessionRepository.startIfWaiting(id, now) == 0) throw ...;   // 여기서 컨텍스트가 비워짐
 * DebateSession session = sessionRepository.getReferenceById(id);   // UPDATE 뒤에 참조를 얻는다
 * participantRepository.save(DebateParticipant.opponent(session, user, stance, now));
 * }</pre>
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
               set s.status = :inProgress, s.topic = :topic, s.startedAt = :now, s.updatedAt = :now
             where s.id = :id and s.status = :waiting
            """)
    int startFriendIfWaiting(@Param("id") Long id, @Param("topic") Topic topic, @Param("now") LocalDateTime now,
                             @Param("waiting") SessionStatus waiting, @Param("inProgress") SessionStatus inProgress);

    default int startFriendIfWaiting(Long id, Topic topic, LocalDateTime now) {
        return startFriendIfWaiting(id, topic, now, SessionStatus.WAITING, SessionStatus.IN_PROGRESS);
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

    /**
     * 진행 중인 게임을 끝냅니다. 시간 종료 스케줄러 · 이탈 처리 · 재시작 정리가 모두 이 메서드로 끝냅니다.
     *
     * <p><b>1 을 받은 쪽만</b> 판정 · 승패 저장 · 종료 알림 · 메모리 정리 같은 후속 처리를 합니다.
     * 0 이면 이미 끝난(또는 시작 전인) 게임이므로 내부 흐름은 무시하고,
     * 사용자 요청이면 {@code SessionErrorCode.SESSION_NOT_IN_PROGRESS} 로 응답합니다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DebateSession s
               set s.status = :finished, s.finishReason = :reason, s.endedAt = :now, s.updatedAt = :now
             where s.id = :id and s.status = :inProgress
            """)
    int finishIfInProgress(@Param("id") Long id, @Param("reason") FinishReason reason, @Param("now") LocalDateTime now,
                           @Param("inProgress") SessionStatus inProgress, @Param("finished") SessionStatus finished);

    default int finishIfInProgress(Long id, FinishReason reason, LocalDateTime now) {
        return finishIfInProgress(id, Objects.requireNonNull(reason, "reason"), now,
                SessionStatus.IN_PROGRESS, SessionStatus.FINISHED);
    }

    Optional<DebateSession> findByInviteCode(String inviteCode);

    /**
     * 서버 재시작 정리용. 메모리에 있던 게임은 이어갈 수 없으므로 IN_PROGRESS 를 {@link #finishIfInProgress} 로 ABORTED 처리합니다.
     * 이 서버가 뜬 뒤 시작된 게임은 이어갈 수 있으므로 {@code startedAt} 이 {@code before} 보다 앞선 세션만 찾습니다.
     */
    List<DebateSession> findAllByStatusAndStartedAtBefore(SessionStatus status, LocalDateTime before);
}
