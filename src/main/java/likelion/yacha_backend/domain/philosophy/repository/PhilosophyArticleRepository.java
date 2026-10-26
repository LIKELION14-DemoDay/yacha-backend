package likelion.yacha_backend.domain.philosophy.repository;

import likelion.yacha_backend.domain.philosophy.entity.PhilosophyArticle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhilosophyArticleRepository extends JpaRepository<PhilosophyArticle, Long> {
}
