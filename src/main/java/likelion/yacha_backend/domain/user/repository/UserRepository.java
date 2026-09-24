package likelion.yacha_backend.domain.user.repository;

import java.util.Optional;
import likelion.yacha_backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;


public interface UserRepository extends JpaRepository<User, Long> {

    /** 회원가입 · 승격에서 이메일 중복을 검사 */
    boolean existsByEmail(String email);

    /** 로그인에서 이메일로 사용자를 찾음 */
    Optional<User> findByEmail(String email);
}
