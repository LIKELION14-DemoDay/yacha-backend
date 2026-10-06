package likelion.yacha_backend.domain.session.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.ParticipantRole;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebateParticipantRepository extends JpaRepository<DebateParticipant, Long> {

    /** 구독 권한 · 전송 검증: 이 사용자가 이 세션의 참가자인가. */
    boolean existsBySession_IdAndUser_Id(Long sessionId, Long userId);

    Optional<DebateParticipant> findBySession_IdAndUser_Id(Long sessionId, Long userId);

    List<DebateParticipant> findAllBySession_Id(Long sessionId);

    /** 방장 · 상대 참가자. 방장은 세션마다 한 명입니다. */
    Optional<DebateParticipant> findBySession_IdAndRole(Long sessionId, ParticipantRole role);

    /**
     * 이미 대기 중이거나 진행 중인 세션이 있는가 ({@code ALREADY_IN_SESSION}).
     * 호출할 때 {@code WAITING}, {@code IN_PROGRESS} 를 넘깁니다.
     */
    boolean existsByUser_IdAndSession_StatusIn(Long userId, Collection<SessionStatus> statuses);

    /**
     * 참가 기록에서 사용자 연결을 끊습니다. 오래된 게스트 계정을 지우기 전에 부릅니다 (users FK).
     * 참가 기록은 남고, AI 참가자처럼 {@code user_id} 가 비게 됩니다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update DebateParticipant p set p.user = null where p.user.id in :userIds")
    int detachUsers(@Param("userIds") Collection<Long> userIds);

    /**
     * 끝난 세션({@code activeStatuses}가 아닌 세션)의 참가 기록에서 이 사용자 연결을 끊습니다. 게스트가 회원으로 승격할 때 부릅니다.
     * 비회원 게임 결과는 전적에 넣지 않기로 해서, 게스트 때 한 경기가 승격 뒤 회원 전적에 섞이지 않게 합니다.
     * 대기 · 진행 중인 세션은 그대로 둡니다. 끊으면 지금 하고 있는 게임에서 참가자로 인정되지 않습니다.
     *
     * @param activeStatuses 그대로 둘 상태. 호출할 때 {@code WAITING}, {@code IN_PROGRESS}를 넘깁니다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DebateParticipant p set p.user = null
            where p.user.id = :userId
              and p.session.id in (select s.id from DebateSession s where s.status not in :activeStatuses)
            """)
    int detachUserFromEndedSessions(@Param("userId") Long userId,
                                    @Param("activeStatuses") Collection<SessionStatus> activeStatuses);
}
