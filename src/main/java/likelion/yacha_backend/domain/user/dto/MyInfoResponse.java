package likelion.yacha_backend.domain.user.dto;

import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.domain.user.entity.User;


public record MyInfoResponse(
        Long userId,
        String nickname,
        String email,
        boolean isGuest,
        /**
         * 가입 경로. 프론트가 "비밀번호 변경" 메뉴를 보여줄지 판단하는 데 사용
         * {@code LOCAL}이 아니면 비밀번호가 없는 계정이라 변경할 것도 없음
         */
        Provider provider,
        StatsResponse stats
) {

    public static MyInfoResponse from(User user, StatsResponse stats) {
        return new MyInfoResponse(user.getId(), user.getNickname(),
                user.getEmail(), user.isGuest(), user.getProvider(), stats);
    }

    public record StatsResponse(int total, int wins, int losses, int draws) {

        public static StatsResponse empty() {
            return new StatsResponse(0, 0, 0, 0);
        }
    }
}
