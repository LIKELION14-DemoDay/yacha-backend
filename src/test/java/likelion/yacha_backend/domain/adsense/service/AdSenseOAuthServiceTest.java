
package likelion.yacha_backend.domain.adsense.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import likelion.yacha_backend.domain.adsense.client.AdSenseTokenClient;
import likelion.yacha_backend.domain.adsense.config.AdSenseProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdSenseOAuthServiceTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> operations;

    @Mock
    AdSenseTokenClient client;

    @Mock
    AdSenseCredentialService credentials;

    AdSenseOAuthService service;

    @BeforeEach
    void setUp() {
        AdSenseProperties properties = new AdSenseProperties(
                true,
                "client",
                "secret",
                "http://localhost:8080/api/v1/adsense/oauth/callback",
                "accounts/pub-123",
                "configured-base64-key",
                false
        );

        service = new AdSenseOAuthService(
                properties,
                redis,
                client,
                credentials
        );
    }

    @Test
    void authorizingCreatesUnpredictableStateAndNonce() {
        when(redis.opsForValue()).thenReturn(operations);

        AdSenseOAuthService.AuthorizationStart result =
                service.start(77L);

        assertTrue(
                result.authorizationUrl().contains("state=")
        );

        assertEquals(43, result.cookieNonce().length());

        verify(operations).set(
                startsWith("adsense:oauth:state:"),
                startsWith("77:"),
                eq(Duration.ofMinutes(10))
        );
    }

    @Test
    void validCallbackConsumesStateThenStoresRefreshToken()
            throws Exception {

        when(redis.opsForValue()).thenReturn(operations);

        AdSenseOAuthService.AuthorizationStart start =
                service.start(77L);

        String state = start.authorizationUrl()
                .substring(
                        start.authorizationUrl().indexOf("state=") + 6
                );

        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(
                                start.cookieNonce().getBytes(
                                        StandardCharsets.UTF_8
                                )
                        )
        );

        when(operations.getAndDelete(
                "adsense:oauth:state:" + state
        )).thenReturn("77:" + digest);

        when(client.exchangeCode("code"))
                .thenReturn(
                        new AdSenseTokenClient.TokenResult(
                                "access",
                                "refresh"
                        )
                );

        service.complete(
                state,
                start.cookieNonce(),
                "code",
                null
        );

        verify(client).verifyExpectedAccount("access");

        verify(credentials).store(
                77L,
                "accounts/pub-123",
                "refresh"
        );
    }

    @Test
    void replayedOrExpiredStateIsRejectedBeforeTokenExchange() {
        when(redis.opsForValue()).thenReturn(operations);

        AdSenseOAuthService.AuthorizationStart start =
                service.start(77L);

        String state = start.authorizationUrl()
                .substring(
                        start.authorizationUrl().indexOf("state=") + 6
                );

        AdSenseOAuthException exception = assertThrows(
                AdSenseOAuthException.class,
                () -> service.complete(
                        state,
                        start.cookieNonce(),
                        "code",
                        null
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.status()
        );

        verifyNoInteractions(client, credentials);
    }

    @Test
    void missingCookieIsRejectedBeforeRedisUse() {
        AdSenseOAuthException exception = assertThrows(
                AdSenseOAuthException.class,
                () -> service.complete(
                        "a".repeat(43),
                        null,
                        "code",
                        null
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.status()
        );

        verifyNoInteractions(
                redis,
                client,
                credentials
        );
    }
}
