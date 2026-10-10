package likelion.yacha_backend.domain.auth.dto;

import likelion.yacha_backend.domain.user.entity.User;


public record AuthResponse(
        String accessToken,
        Long userId,
        String nickname,
        boolean isGuest,
        boolean isNewUser
) {

    public static AuthResponse from(String accessToken, User user, boolean isNewUser) {
        return new AuthResponse(accessToken, user.getId(), user.getNickname(), user.isGuest(), isNewUser);
    }
}
