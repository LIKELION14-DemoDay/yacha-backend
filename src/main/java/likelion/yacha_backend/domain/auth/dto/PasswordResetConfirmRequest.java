package likelion.yacha_backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import likelion.yacha_backend.global.validation.MaxBytes;

/** 메일 링크로 들어와 새 비밀번호를 정하는 요청 */
public record PasswordResetConfirmRequest(

        @NotBlank(message = "토큰은 필수입니다.")
        String token,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        // @Size는 글자 수, BCrypt는 72바이트 제한이라 바이트도 따로 검사 (가입 · 변경과 같은 규칙)
        @MaxBytes(value = 72, message = "비밀번호가 너무 깁니다. (한글은 한 글자가 3바이트입니다)")
        String newPassword
) {
}
