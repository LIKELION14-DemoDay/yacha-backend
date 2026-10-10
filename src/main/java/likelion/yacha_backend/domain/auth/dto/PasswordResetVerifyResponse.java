package likelion.yacha_backend.domain.auth.dto;

/** @param resetToken 비밀번호 재설정(/auth/password/reset)의 token 자리에 넣는 값. 10분, 1회용 */
public record PasswordResetVerifyResponse(String resetToken) {
}
