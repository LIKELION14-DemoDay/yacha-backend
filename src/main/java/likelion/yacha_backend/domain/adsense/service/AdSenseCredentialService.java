
package likelion.yacha_backend.domain.adsense.service;

import java.time.Clock;
import java.time.LocalDateTime;

import likelion.yacha_backend.domain.adsense.entity.AdSenseCredential;
import likelion.yacha_backend.domain.adsense.repository.AdSenseCredentialRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdSenseCredentialService {

    private final AdSenseCredentialRepository repository;
    private final AdSenseTokenCipher tokenCipher;
    private final Clock clock;

    public AdSenseCredentialService(
            AdSenseCredentialRepository repository,
            AdSenseTokenCipher tokenCipher,
            @Qualifier("adsenseClock") Clock clock
    ) {
        this.repository = repository;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
    }

    @Transactional
    public void store(
            Long adminUserId,
            String accountName,
            String refreshToken
    ) {
        String ciphertext = tokenCipher.encrypt(refreshToken);

        LocalDateTime connectedAt = LocalDateTime.now(clock);

        AdSenseCredential existing = repository
                .findById(AdSenseCredential.SINGLE_ACCOUNT_ID)
                .orElse(null);

        if (existing == null) {
            repository.save(
                    AdSenseCredential.initial(
                            accountName,
                            ciphertext,
                            adminUserId,
                            connectedAt
                    )
            );
        } else {
            existing.update(
                    accountName,
                    ciphertext,
                    adminUserId,
                    connectedAt
            );
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
                credential.getEncryptedRefreshToken()
        );
    }
}
