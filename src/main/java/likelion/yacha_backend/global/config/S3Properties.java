package likelion.yacha_backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "storage.s3")
public record S3Properties(
        String bucket,
        String region,
        String profilePrefix,
        Duration presignedUrlValidity
) {
}