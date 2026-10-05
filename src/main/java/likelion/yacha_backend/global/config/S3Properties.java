package likelion.yacha_backend.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "storage.s3")
public record S3Properties(
        @NotBlank String bucket,
        @NotBlank String region,
        @NotBlank String profilePrefix,
        @NotNull Duration presignedUrlValidity
) {

    public S3Properties {
        if (presignedUrlValidity != null
                && (presignedUrlValidity.isZero() || presignedUrlValidity.isNegative())) {
            throw new IllegalArgumentException(
                    "storage.s3.presigned-url-validity must be positive"
            );
        }
    }
}
