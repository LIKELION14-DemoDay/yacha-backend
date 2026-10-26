package likelion.yacha_backend.domain.philosophy.repository;

import java.util.Optional;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyMemo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhilosophyMemoRepository extends JpaRepository<PhilosophyMemo, Long> {

    Optional<PhilosophyMemo> findByUser_IdAndArticle_Id(Long userId, Long articleId);
}
