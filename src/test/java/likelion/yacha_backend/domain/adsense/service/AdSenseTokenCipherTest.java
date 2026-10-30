
package likelion.yacha_backend.domain.adsense.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import likelion.yacha_backend.domain.adsense.config.AdSenseProperties;
import org.junit.jupiter.api.Test;

class AdSenseTokenCipherTest {

    private static AdSenseTokenCipher cipher() {
        String key = Base64.getEncoder()
                .encodeToString(new byte[32]);

        AdSenseProperties properties = new AdSenseProperties(
                true,
                "id",
                "secret",
                "uri",
                "accounts/pub-123",
                key,
                false
        );

        return new AdSenseTokenCipher(properties);
    }

    @Test
    void roundTripAndRandomIv() {
        AdSenseTokenCipher cipher = cipher();

        String first = cipher.encrypt("test-refresh-token");
        String second = cipher.encrypt("test-refresh-token");

        assertNotEquals(first, second);

        assertEquals(
                "test-refresh-token",
                cipher.decrypt(first)
        );

        assertEquals(
                "test-refresh-token",
                cipher.decrypt(second)
        );
    }

    @Test
    void alteredCiphertextIsRejected() {
        AdSenseTokenCipher cipher = cipher();
        String encrypted = cipher.encrypt("secret");

        byte[] bytes = Base64.getDecoder().decode(
                encrypted.substring(3)
        );

        bytes[bytes.length - 1] ^= 1;

        String tampered = "v1:"
                + Base64.getEncoder().encodeToString(bytes);

        assertThrows(
                AdSenseOAuthException.class,
                () -> cipher.decrypt(tampered)
        );
    }
}
