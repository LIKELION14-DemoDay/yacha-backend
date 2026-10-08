package likelion.yacha_backend.global.storage;

import likelion.yacha_backend.global.config.S3Properties;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final long MAX_PROFILE_IMAGE_SIZE = 5 * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    /**
     * 프로필 이미지를 S3에 업로드하고 DB에 저장할 object key를 반환합니다.
     *
     * 예:
     * profiles/12/550e8400-e29b-41d4-a716-446655440000.jpg
     */
    public String uploadProfileImage(Long userId, MultipartFile file) {
        validateProfileImage(file);

        String extension = extensionFromContentType(file.getContentType());
        String key = createProfileImageKey(userId, extension);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(inputStream, file.getSize())
            );

            return key;
        } catch (IOException | SdkException e) {
            throw new BusinessException(StorageErrorCode.S3_UPLOAD_FAILED, e);
        }
    }

    /**
     * private S3 객체를 프론트에서 일정 시간 조회할 수 있도록
     * presigned GET URL을 생성합니다.
     *
     * 이 URL은 만료되므로 DB에 저장하지 않습니다.
     */
    public String createReadUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(properties.presignedUrlValidity())
                        .getObjectRequest(getObjectRequest)
                        .build();

        try {
            return s3Presigner
                    .presignGetObject(presignRequest)
                    .url()
                    .toString();
        } catch (SdkException e) {
            throw new BusinessException(
                    StorageErrorCode.S3_URL_GENERATION_FAILED,
                    e
            );
        }
    }

    /**
     * S3 객체를 삭제합니다.
     * 기존 프로필 이미지가 없는 경우(null/blank)는 아무 작업도 하지 않습니다.
     */
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException e) {
            throw new BusinessException(StorageErrorCode.S3_DELETE_FAILED, e);
        }
    }

    private void validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(StorageErrorCode.EMPTY_FILE);
        }

        if (file.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new BusinessException(StorageErrorCode.FILE_TOO_LARGE);
        }

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(StorageErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
    }

    private String createProfileImageKey(Long userId, String extension) {
        return properties.profilePrefix()
                + "/"
                + userId
                + "/"
                + UUID.randomUUID()
                + extension;
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new BusinessException(
                    StorageErrorCode.UNSUPPORTED_IMAGE_TYPE
            );
        };
    }
}