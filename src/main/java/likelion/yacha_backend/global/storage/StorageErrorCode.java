package likelion.yacha_backend.global.storage;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum StorageErrorCode implements BaseErrorCode {

    EMPTY_FILE(
            HttpStatus.BAD_REQUEST,
            "업로드할 이미지 파일이 비어 있습니다."
    ),

    FILE_TOO_LARGE(
            HttpStatus.BAD_REQUEST,
            "프로필 이미지는 5MB 이하여야 합니다."
    ),

    UNSUPPORTED_IMAGE_TYPE(
            HttpStatus.BAD_REQUEST,
            "JPEG, PNG, WEBP 형식의 이미지만 업로드할 수 있습니다."
    ),

    S3_UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "이미지 업로드 중 오류가 발생했습니다."
    ),

    S3_DELETE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "이미지 삭제 중 오류가 발생했습니다."
    ),

    S3_URL_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "이미지 조회 URL 생성 중 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String message;

    StorageErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}