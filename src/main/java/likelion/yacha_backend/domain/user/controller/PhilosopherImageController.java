
package likelion.yacha_backend.domain.user.controller;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import likelion.yacha_backend.global.config.S3Properties;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.storage.StorageErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RestController
@RequestMapping("/api/v1/philosopher-images")
@RequiredArgsConstructor
public class PhilosopherImageController {

    private static final Set<String> ALLOWED_NAMES = Set.of(
            "kant",
            "hobbes",
            "sartre",
            "rousseau",
            "mill",
            "epictetus",
            "nietzsche",
            "epicurus",
            "default",
            "bot"
    );

    private final S3Client s3Client;
    private final S3Properties properties;

    @GetMapping("/{filename:.+}")
    public ResponseEntity<byte[]> getImage(
            @PathVariable String filename
    ) {
        boolean isSvg = filename.endsWith(".svg");
        boolean isPng = filename.endsWith(".png");

        if (!isSvg && !isPng) {
            throw new BusinessException(StorageErrorCode.IMAGE_NOT_FOUND);
        }

        String name = filename.substring(
                0, filename.lastIndexOf('.')
        );

        if (!ALLOWED_NAMES.contains(name)) {
            throw new BusinessException(StorageErrorCode.IMAGE_NOT_FOUND);
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key("philosophers/" + filename)
                    .build();

            ResponseBytes<GetObjectResponse> object =
                    s3Client.getObjectAsBytes(request);

            MediaType contentType = isSvg
                    ? MediaType.parseMediaType("image/svg+xml")
                    : MediaType.IMAGE_PNG;

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .cacheControl(
                            CacheControl.maxAge(1, TimeUnit.HOURS)
                                    .cachePublic()
                    )
                    .body(object.asByteArray());

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new BusinessException(StorageErrorCode.IMAGE_NOT_FOUND);
            }

            throw new BusinessException(StorageErrorCode.S3_IMAGE_READ_FAILED, e);
        } catch (SdkException e) {
            throw new BusinessException(StorageErrorCode.S3_IMAGE_READ_FAILED, e);
        }
    }
}
