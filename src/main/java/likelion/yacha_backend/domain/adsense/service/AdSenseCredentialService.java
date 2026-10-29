
package likelion.yacha_backend.domain.adsense.service;

import likelion.yacha_backend.domain.adsense.entity.AdSenseCredential;
import likelion.yacha_backend.domain.adsense.repository.AdSenseCredentialRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdSenseCredentialService {
    private final AdSenseCredentialRepository repository;
    private final AdSenseTokenCipher tokenCipher;

    public AdSenseCredentialService(
            AdSenseCredentialRepository repository,
            AdSenseTokenCipher tokenCipher
    ) {
        this.repository = repository;
        this.tokenCipher = tokenCipher;
    }

    @Transactional
    public void store(Long adminUserId, String accountName, String refreshToken) {
        String ciphertext = tokenCipher.encrypt(refreshToken);

        AdSenseCredential existing = repository
                .findById(AdSenseCredential.SINGLE_ACCOUNT_ID)
                .orElse(null);

        if (existing == null) {
            repository.save(AdSenseCredential.initial(
                    accountName, ciphertext, adminUserId));
        } else {
            existing.update(accountName, ciphertext, adminUserId);
        }
    }

    @Transactional(readOnly = true)
    public String getRefreshToken() {
        AdSenseCredential credential = repository
                .findById(AdSenseCredential.SINGLE_ACCOUNT_ID)
                .orElseThrow(() -> new AdSenseOAuthException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "아직 AdSense 계정이 연결되지 않았습니다."
                ));

        return tokenCipher.decrypt(
                credential.getEncryptedRefreshToken());
    }
}
