
package likelion.yacha_backend.domain.user.controller;

import java.util.Set;
import likelion.yacha_backend.global.config.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/philosopher-images")
@RequiredArgsConstructor
public class PhilosopherImageController {

    private static final Set<String> ALLOWED = Set.of(
            "kant.png",
            "hobbes.png",
            "sartre.png",
            "rousseau.png",
            "mill.png",
            "epictetus.png",
            "nietzsche.png",
            "epicurus.png",
            "default.png",
            "bot.png"
    );

    private final S3Client s3Client;
    private final S3Properties properties;

    @GetMapping("/{filename:.+}")
    public ResponseEntity<byte[]> getImage(
            @PathVariable String filename
    ) {
        if (!ALLOWED.contains(filename)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key("philosophers/" + filename)
                    .build();

            ResponseBytes<GetObjectResponse> object =
                    s3Client.getObjectAsBytes(request);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .body(object.asByteArray());

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "이미지를 불러오지 못했습니다.",
                    e
            );
        }
    }
}
