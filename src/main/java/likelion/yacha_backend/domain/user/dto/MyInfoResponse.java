package likelion.yacha_backend.domain.user.dto;

import likelion.yacha_backend.domain.user.entity.User;


public record MyInfoResponse(
        Long userId,
        String nickname,
        String email,
        boolean isGuest,
        StatsResponse stats
) {

    public static MyInfoResponse from(User user, StatsResponse stats) {
        return new MyInfoResponse(user.getId(), user.getNickname(),
                user.getEmail(), user.isGuest(), stats);
    }

    public record StatsResponse(int total, int wins, int losses, int draws) {

        public static StatsResponse empty() {
            return new StatsResponse(0, 0, 0, 0);
        }
    }
}
