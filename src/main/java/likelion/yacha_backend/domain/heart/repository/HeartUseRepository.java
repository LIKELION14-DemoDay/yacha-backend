package likelion.yacha_backend.domain.heart.repository;

import java.util.Collection;
import java.util.List;
import likelion.yacha_backend.domain.heart.entity.HeartUse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HeartUseRepository extends JpaRepository<HeartUse, Long> {

    /** 이 판에서 하트를 쓰고 아직 돌려받지 않은 사용자 id */
    @Query("select u.user.id from HeartUse u where u.session.id = :sessionId and u.refunded = false")
    List<Long> findUnrefundedUserIds(@Param("sessionId") Long sessionId);

    /**
     * 아직 돌려주지 않았을 때만 돌려준 것으로 바꾸고, 바꾼 행 수를 돌려줌 (조건부 UPDATE)
     * 무효 처리와 판정 실패 처리가 겹쳐도 한쪽만 1을 받아 하트를 한 번만 돌려줌
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update HeartUse u set u.refunded = true
            where u.session.id = :sessionId and u.user.id = :userId and u.refunded = false
            """)
    int markRefunded(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    /** 게스트 정리: 계정을 지우기 전에 하트 사용 기록을 지움 (users FK) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from HeartUse u where u.user.id in :userIds")
    int deleteAllByUserIds(@Param("userIds") Collection<Long> userIds);
}
