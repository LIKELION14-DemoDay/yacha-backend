package likelion.yacha_backend.global.storage;

import likelion.yacha_backend.global.config.S3Properties;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("S3StorageService")
class S3StorageServiceTest {

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private S3StorageService storageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);

        S3Properties properties = new S3Properties(
                "test-bucket",
                "ap-northeast-2",
                "profiles",
                Duration.ofHours(1)
        );

        storageService = new S3StorageService(
                s3Client,
                s3Presigner,
                properties
        );
    }

    @Test
    @DisplayName("정상 이미지를 업로드하면 profiles/{userId}/{uuid}.확장자 key를 반환한다")
    void uploadProfileImageSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "profile.jpg",
                "image/jpeg",
                "image-data".getBytes()
        );

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenReturn(PutObjectResponse.builder().build());

        String key = storageService.uploadProfileImage(12L, file);

        assertThat(key)
                .startsWith("profiles/12/")
                .endsWith(".jpg");

        ArgumentCaptor<PutObjectRequest> captor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        verify(s3Client).putObject(
                captor.capture(),
                any(RequestBody.class)
        );

        PutObjectRequest request = captor.getValue();

        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(key);
        assertThat(request.contentType()).isEqualTo("image/jpeg");
        assertThat(request.contentLength()).isEqualTo(file.getSize());
    }

    @Test
    @DisplayName("빈 파일이면 업로드하지 않고 EMPTY_FILE 오류를 낸다")
    void emptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThatThrownBy(() ->
                storageService.uploadProfileImage(1L, file)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(
                        ((BusinessException) e).getErrorCode()
                ).isEqualTo(StorageErrorCode.EMPTY_FILE));

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("5MB를 초과하면 FILE_TOO_LARGE 오류를 낸다")
    void fileTooLarge() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "large.jpg",
                "image/jpeg",
                new byte[5 * 1024 * 1024 + 1]
        );

        assertThatThrownBy(() ->
                storageService.uploadProfileImage(1L, file)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(
                        ((BusinessException) e).getErrorCode()
                ).isEqualTo(StorageErrorCode.FILE_TOO_LARGE));

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("허용되지 않은 형식이면 UNSUPPORTED_IMAGE_TYPE 오류를 낸다")
    void unsupportedImageType() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "profile.gif",
                "image/gif",
                "gif-data".getBytes()
        );

        assertThatThrownBy(() ->
                storageService.uploadProfileImage(1L, file)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(
                        ((BusinessException) e).getErrorCode()
                ).isEqualTo(StorageErrorCode.UNSUPPORTED_IMAGE_TYPE));

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("key가 있으면 해당 S3 객체를 삭제한다")
    void deleteSuccess() {
        String key = "profiles/12/test-image.jpg";

        storageService.delete(key);

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);

        verify(s3Client).deleteObject(captor.capture());

        DeleteObjectRequest request = captor.getValue();

        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(key);
    }

    @Test
    @DisplayName("삭제할 key가 없으면 S3를 호출하지 않는다")
    void deleteBlankKey() {
        storageService.delete(null);
        storageService.delete("");
        storageService.delete("   ");

        verifyNoInteractions(s3Client);
    }
}