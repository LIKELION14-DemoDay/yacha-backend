package likelion.yacha_backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("S3Properties — storage.s3.* 설정 검증")
class S3PropertiesTest {

    @Configuration
    @EnableConfigurationProperties(S3Properties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    private static final String BUCKET =
            "storage.s3.bucket=test-bucket";

    private static final String REGION =
            "storage.s3.region=ap-northeast-2";

    private static final String PROFILE_PREFIX =
            "storage.s3.profile-prefix=profiles";

    private static final String VALIDITY =
            "storage.s3.presigned-url-validity=1h";

    @Test
    @DisplayName("필수 설정값이 모두 있으면 기동한다")
    void valid() {
        runner.withPropertyValues(
                        BUCKET,
                        REGION,
                        PROFILE_PREFIX,
                        VALIDITY
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("bucket이 비어 있으면 기동이 실패한다")
    void blankBucket() {
        runner.withPropertyValues(
                        "storage.s3.bucket=",
                        REGION,
                        PROFILE_PREFIX,
                        VALIDITY
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("region이 비어 있으면 기동이 실패한다")
    void blankRegion() {
        runner.withPropertyValues(
                        BUCKET,
                        "storage.s3.region=",
                        PROFILE_PREFIX,
                        VALIDITY
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("profile-prefix가 비어 있으면 기동이 실패한다")
    void blankProfilePrefix() {
        runner.withPropertyValues(
                        BUCKET,
                        REGION,
                        "storage.s3.profile-prefix=",
                        VALIDITY
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("presigned-url-validity가 없으면 기동이 실패한다")
    void missingPresignedUrlValidity() {
        runner.withPropertyValues(
                        BUCKET,
                        REGION,
                        PROFILE_PREFIX
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("presigned-url-validity가 0이면 기동이 실패한다")
    void zeroPresignedUrlValidity() {
        runner.withPropertyValues(
                        BUCKET,
                        REGION,
                        PROFILE_PREFIX,
                        "storage.s3.presigned-url-validity=0s"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("presigned-url-validity가 음수이면 기동이 실패한다")
    void negativePresignedUrlValidity() {
        runner.withPropertyValues(
                        BUCKET,
                        REGION,
                        PROFILE_PREFIX,
                        "storage.s3.presigned-url-validity=-1s"
                )
                .run(context -> assertThat(context).hasFailed());
    }

}
