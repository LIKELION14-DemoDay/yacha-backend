package likelion.yacha_backend.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 이메일 중복 확인 ({@code ?email=})
 *
 * 검증은 {@link SignupRequest}의 이메일과 같게 둠. 여기서 통과한 이메일이 가입 때 400이 나면 안 되기 때문
 */
public record EmailAvailabilityRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email
) {
}
