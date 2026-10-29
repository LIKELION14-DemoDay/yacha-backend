
package likelion.yacha_backend.domain.user.service;

import java.util.Locale;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.global.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PhilosopherImageUrlService {

    private final S3StorageService s3StorageService;

    public String readUrl(PhilosopherType type) {
        String filename = type == null
                ? "default.png"
                : type.name().toLowerCase(Locale.ROOT) + ".png";

        String key = "philosophers/" + filename;

        return s3StorageService.createReadUrl(key);
    }
}
