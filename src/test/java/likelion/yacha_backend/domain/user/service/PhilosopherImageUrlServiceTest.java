
package likelion.yacha_backend.domain.user.service;

import java.util.Locale;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.global.storage.S3StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("철학자 이미지 URL 매핑 테스트")
class PhilosopherImageUrlServiceTest {

    private final S3StorageService s3StorageService =
            mock(S3StorageService.class);

    private final PhilosopherImageUrlService imageUrlService =
            new PhilosopherImageUrlService(s3StorageService);

    @Test
    @DisplayName("철학자 8명 모두 올바른 S3 이미지 경로를 사용한다")
    void philosopherImageMapping() {

        for (PhilosopherType type : PhilosopherType.values()) {

            String filename =
                    type.name().toLowerCase(Locale.ROOT) + ".png";

            String key = "philosophers/" + filename;

            String expectedUrl =
                    "https://example.com/" + key;

            when(s3StorageService.createReadUrl(key))
                    .thenReturn(expectedUrl);

            String actualUrl = imageUrlService.readUrl(type);

            assertThat(actualUrl).isEqualTo(expectedUrl);

            verify(s3StorageService).createReadUrl(key);
        }
    }

    @Test
    @DisplayName("철학자 유형이 없으면 기본 이미지를 사용한다")
    void defaultImageMapping() {

        String key = "philosophers/default.png";
        String expectedUrl =
                "https://example.com/" + key;

        when(s3StorageService.createReadUrl(key))
                .thenReturn(expectedUrl);

        String actualUrl = imageUrlService.readUrl(null);

        assertThat(actualUrl).isEqualTo(expectedUrl);

        verify(s3StorageService).createReadUrl(key);
    }
}
