package likelion.yacha_backend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 메일로 받은 인증번호를 확인하는 요청. 이메일 검증은 재설정 요청과 같음 */
public record PasswordResetVerifyRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        // 형식이 틀린 값은 시도 횟수를 쓰지 않고 400으로 끝냄
        @NotBlank(message = "인증번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "인증번호는 숫자 6자리입니다.")
        String code
) {
}
