package likelion.yacha_backend.domain.philosophy.repository;

import likelion.yacha_backend.domain.philosophy.entity.PhilosophyMemo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhilosophyMemoRepository extends JpaRepository<PhilosophyMemo, Long> {
}
