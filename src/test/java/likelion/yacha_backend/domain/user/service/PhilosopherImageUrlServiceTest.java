
package likelion.yacha_backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import org.junit.jupiter.api.Test;

class PhilosopherImageUrlServiceTest {

    private final PhilosopherImageUrlService service =
            new PhilosopherImageUrlService(
                    "http://localhost:8080/api/v1/philosopher-images"
            );

    @Test
    void mapsAllPhilosophers() {
        for (PhilosopherType type : PhilosopherType.values()) {
            String filename =
                    type.name().toLowerCase(Locale.ROOT) + ".svg";

            assertThat(service.readUrl(type))
                    .endsWith("/" + filename);
        }
    }

    @Test
    void mapsDefaultAndBot() {
        assertThat(service.readUrl(null))
                .endsWith("/default.svg");

        assertThat(service.botUrl())
                .endsWith("/bot.svg");
    }
}
