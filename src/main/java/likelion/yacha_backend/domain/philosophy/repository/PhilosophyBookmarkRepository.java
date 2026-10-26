package likelion.yacha_backend.domain.philosophy.repository;

import likelion.yacha_backend.domain.philosophy.entity.PhilosophyBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhilosophyBookmarkRepository extends JpaRepository<PhilosophyBookmark, Long> {
}
