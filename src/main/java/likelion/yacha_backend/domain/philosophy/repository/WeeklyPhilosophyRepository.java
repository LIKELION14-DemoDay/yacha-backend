package likelion.yacha_backend.domain.philosophy.repository;

import likelion.yacha_backend.domain.philosophy.entity.WeeklyPhilosophy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyPhilosophyRepository extends JpaRepository<WeeklyPhilosophy, Long> {
}
