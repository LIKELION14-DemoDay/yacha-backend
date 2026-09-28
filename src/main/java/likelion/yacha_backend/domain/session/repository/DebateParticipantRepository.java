package likelion.yacha_backend.domain.session.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebateParticipantRepository extends JpaRepository<DebateParticipant, Long> {

    /** 구독 권한 · 전송 검증: 이 사용자가 이 세션의 참가자인가. */
    boolean existsBySession_IdAndUser_Id(Long sessionId, Long userId);

    Optional<DebateParticipant> findBySession_IdAndUser_Id(Long sessionId, Long userId);

    List<DebateParticipant> findAllBySession_Id(Long sessionId);

    /**
     * 이미 대기 중이거나 진행 중인 세션이 있는가 ({@code ALREADY_IN_SESSION}).
     * 호출할 때 {@code WAITING}, {@code IN_PROGRESS} 를 넘깁니다.
     */
    boolean existsByUser_IdAndSession_StatusIn(Long userId, Collection<SessionStatus> statuses);
}
