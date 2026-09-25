package likelion.yacha_backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 *  닉네임 변경 요청
 *  닉네임에는 UNIQUE 제약이 없어 중복 허용
 */
public record NicknameUpdateRequest(

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        String nickname
) {
}
