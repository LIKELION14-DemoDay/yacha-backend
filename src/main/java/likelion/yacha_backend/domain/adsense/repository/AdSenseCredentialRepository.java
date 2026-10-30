package likelion.yacha_backend.domain.adsense.repository;

import likelion.yacha_backend.domain.adsense.entity.AdSenseCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdSenseCredentialRepository extends JpaRepository<AdSenseCredential, String> {
}
