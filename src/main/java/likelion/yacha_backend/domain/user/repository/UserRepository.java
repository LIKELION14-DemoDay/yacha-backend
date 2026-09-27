package likelion.yacha_backend.domain.user.repository;

import java.util.Optional;
import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;


public interface UserRepository extends JpaRepository<User, Long> {

    /** 회원가입 · 승격에서 이메일 중복을 검사 */
    boolean existsByEmail(String email);

    /** 로그인에서 이메일로 사용자를 찾음 */
    Optional<User> findByEmail(String email);

    /**
     * 소셜 로그인에서 계정을 찾음. 이메일이 아니라 provider + sub 로 찾는다.
     * (이메일은 사용자가 바꿀 수 있어 식별자로 쓰면 안 됨)
     */
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
}
