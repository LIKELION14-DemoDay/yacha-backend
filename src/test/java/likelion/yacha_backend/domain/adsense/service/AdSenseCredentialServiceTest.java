
package likelion.yacha_backend.domain.adsense.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import likelion.yacha_backend.domain.adsense.entity.AdSenseCredential;
import likelion.yacha_backend.domain.adsense.repository.AdSenseCredentialRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdSenseCredentialServiceTest {

    @Mock
    private AdSenseCredentialRepository repository;

    @Mock
    private AdSenseTokenCipher tokenCipher;

    private AdSenseCredentialService service;

    private static final LocalDateTime EXPECTED_TIME =
            LocalDateTime.of(2026, 10, 30, 10, 30);

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-10-30T01:30:00Z"),
                ZoneId.of("Asia/Seoul")
        );

        service = new AdSenseCredentialService(
                repository,
                tokenCipher,
                fixedClock
        );
    }

    @Test
    void newCredentialUsesInjectedClock() {
        when(tokenCipher.encrypt("refresh-token"))
                .thenReturn("encrypted-token");

        when(repository.findById(
                AdSenseCredential.SINGLE_ACCOUNT_ID
        )).thenReturn(Optional.empty());

        service.store(
                2L,
                "accounts/pub-123",
                "refresh-token"
        );

        ArgumentCaptor<AdSenseCredential> captor =
                ArgumentCaptor.forClass(AdSenseCredential.class);

        verify(repository).save(captor.capture());

        AdSenseCredential saved = captor.getValue();

        assertEquals(EXPECTED_TIME, saved.getConnectedAt());
        assertEquals(2L, saved.getConnectedBy());
        assertEquals("encrypted-token",
                saved.getEncryptedRefreshToken());
    }

    @Test
    void existingCredentialUpdatesWithInjectedClock() {
        AdSenseCredential existing =
                AdSenseCredential.initial(
                        "accounts/pub-123",
                        "old-encrypted-token",
                        1L,
                        LocalDateTime.of(2026, 10, 29, 12, 0)
                );

        when(tokenCipher.encrypt("new-refresh-token"))
                .thenReturn("new-encrypted-token");

        when(repository.findById(
                AdSenseCredential.SINGLE_ACCOUNT_ID
        )).thenReturn(Optional.of(existing));

        service.store(
                2L,
                "accounts/pub-123",
                "new-refresh-token"
        );

        assertEquals(
                EXPECTED_TIME,
                existing.getConnectedAt()
        );
        assertEquals(2L, existing.getConnectedBy());
        assertEquals(
                "new-encrypted-token",
                existing.getEncryptedRefreshToken()
        );

        verify(repository, never()).save(any());
    }
}
