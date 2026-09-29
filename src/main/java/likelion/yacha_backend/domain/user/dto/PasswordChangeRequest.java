package likelion.yacha_backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import likelion.yacha_backend.global.validation.MaxBytes;

/**
 * 비밀번호 변경 요청
 * 현재 비밀번호에는 길이 규칙을 걸지 않음
 */
public record PasswordChangeRequest(

        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        // 가입과 같은 규칙
        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        // @Size는 글자 수, BCrypt는 72바이트 제한이라 바이트도 따로 검사
        // 한글 30자(90바이트)처럼 통과시키면 encode()에서 예외가 나 500이 됨
        @MaxBytes(value = 72, message = "비밀번호가 너무 깁니다. (한글은 한 글자가 3바이트입니다)")
        String newPassword
) {
}
