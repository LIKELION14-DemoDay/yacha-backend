
package likelion.yacha_backend.domain.user.service;

import java.util.Locale;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PhilosopherImageUrlService {

    private final String baseUrl;

    public PhilosopherImageUrlService(
            @Value("${philosopher.image.base-url}") String baseUrl
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "philosopher.image.base-url must not be blank"
            );
        }

        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String readUrl(PhilosopherType type) {
        String filename = type == null
                ? "default.svg"
                : type.name().toLowerCase(Locale.ROOT) + ".svg";

        return baseUrl + "/" + filename;
    }

    public String botUrl() {
        return baseUrl + "/bot.svg";
    }
}
