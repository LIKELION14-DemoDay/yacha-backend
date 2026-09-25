package likelion.yacha_backend.domain.auth.dto;

import likelion.yacha_backend.domain.user.entity.User;


public record AuthResponse(
        String accessToken,
        Long userId,
        String nickname,
        boolean isGuest
) {

    public static AuthResponse from(String accessToken, User user) {
        return new AuthResponse(accessToken, user.getId(), user.getNickname(), user.isGuest());
    }
}
