package likelion.yacha_backend.domain.heart.repository;

import java.util.Collection;
import likelion.yacha_backend.domain.heart.entity.UserHeart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserHeartRepository extends JpaRepository<UserHeart, Long> {

    /** 게스트 정리: 계정을 지우기 전에 하트 행을 지움 (users FK) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserHeart h where h.userId in :userIds")
    int deleteAllByUserIds(@Param("userIds") Collection<Long> userIds);
}
