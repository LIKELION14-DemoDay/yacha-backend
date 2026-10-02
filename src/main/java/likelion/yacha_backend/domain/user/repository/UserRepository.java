package likelion.yacha_backend.domain.user.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.domain.user.entity.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface UserRepository extends JpaRepository<User, Long> {

    /** 회원가입 · 승격에서 이메일 중복을 검사 */
    boolean existsByEmail(String email);

    /** 로그인에서 이메일로 사용자를 찾음 */
    Optional<User> findByEmail(String email);

    /** 소셜 로그인에서 이메일이 아니라 provider + sub로 계정을 찾음 */
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    /**
     * 게스트 정리 후보: {@code before}보다 먼저 만들어진 게스트 id를 {@code afterId} 다음부터 id 순으로
     * 정리하지 않고 남긴 후보를 다시 읽지 않도록 id 커서로 넘김
     */
    @Query("""
            select u.id from User u
            where u.isGuest = true and u.createdAt < :before and u.id > :afterId
            order by u.id
            """)
    List<Long> findGuestIdsCreatedBefore(@Param("before") LocalDateTime before,
                                         @Param("afterId") long afterId,
                                         Limit limit);

    /**
     * 게스트 정리: 넘긴 id 중 아직 게스트인 행만 잠그며 읽음 (SELECT ... FOR UPDATE)
     * 잠금은 정리 트랜잭션이 끝날 때까지 유지돼, 그 사이 같은 계정의 승격은 기다림
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id in :ids and u.isGuest = true")
    List<User> findGuestsByIdForUpdate(@Param("ids") Collection<Long> ids);
}
