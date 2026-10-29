package likelion.yacha_backend.domain.adsense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adsense")
public record AdSenseProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String redirectUri,
        String expectedAccountName,
        String tokenEncryptionKey,
        boolean cookieSecure
) {
}
