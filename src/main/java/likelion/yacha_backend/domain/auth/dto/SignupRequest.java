package likelion.yacha_backend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import likelion.yacha_backend.global.validation.MaxBytes;


public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        // @Size는 글자 수, BCrypt는 72바이트 제한이라 바이트도 따로 검사
        // 한글 30자(90바이트)처럼 통과시키면 encode()에서 예외가 나 500이 됨
        @MaxBytes(value = 72, message = "비밀번호가 너무 깁니다. (한글은 한 글자가 3바이트입니다)")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        String nickname
) {
}
