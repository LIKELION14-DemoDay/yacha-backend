
package likelion.yacha_backend.domain.adsense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "adsense_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdSenseCredential {

    public static final String SINGLE_ACCOUNT_ID = "primary";

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "account_name", nullable = false, length = 128)
    private String accountName;

    @Column(name = "encrypted_refresh_token", nullable = false, length = 2048)
    private String encryptedRefreshToken;

    @Column(name = "connected_by", nullable = false)
    private Long connectedBy;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Version
    private Long version;

    public static AdSenseCredential initial(
            String accountName,
            String ciphertext,
            Long connectedBy,
            LocalDateTime connectedAt
    ) {
        AdSenseCredential result = new AdSenseCredential();
        result.id = SINGLE_ACCOUNT_ID;
        result.update(
                accountName,
                ciphertext,
                connectedBy,
                connectedAt
        );
        return result;
    }

    public void update(
            String accountName,
            String ciphertext,
            Long connectedBy,
            LocalDateTime connectedAt
    ) {
        this.accountName = accountName;
        this.encryptedRefreshToken = ciphertext;
        this.connectedBy = connectedBy;
        this.connectedAt = connectedAt;
    }
}
