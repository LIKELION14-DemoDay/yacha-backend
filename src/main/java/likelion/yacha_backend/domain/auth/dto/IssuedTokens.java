package likelion.yacha_backend.domain.auth.dto;

import likelion.yacha_backend.domain.user.entity.User;

/** 서비스가 컨트롤러에게 돌려주는 내부 전달용 값 */
public record IssuedTokens(String accessToken, String refreshToken, User user) {

    public AuthResponse toResponse() {
        return AuthResponse.from(accessToken, user);
    }
}
