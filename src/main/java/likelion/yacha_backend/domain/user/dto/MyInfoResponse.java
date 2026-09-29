package likelion.yacha_backend.domain.user.dto;

import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.domain.user.entity.User;


public record MyInfoResponse(
        Long userId,
        String nickname,
        String email,
        boolean isGuest,
        /**
         * 가입 경로
         * 프론트가 "비밀번호 변경" 메뉴를 보여줄지 판단하는 데 사용
         *
         * 게스트도 LOCAL (User.createGuest가 LOCAL로 만듦)
         * 그래서 비밀번호가 있는 계정은 provider == LOCAL && !isGuest인 경우뿐
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
