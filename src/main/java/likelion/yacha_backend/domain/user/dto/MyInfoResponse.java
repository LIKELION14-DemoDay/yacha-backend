package likelion.yacha_backend.domain.user.dto;

import likelion.yacha_backend.domain.user.entity.BalanceGameStatus;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
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
        StatsResponse stats,
        /** 철학 유형 (밸런스 게임 결과). 없으면 type 이 null 이고 이름은 "일반인" */
        PhilosopherSummary philosopher,
        /** 홈에 들어왔을 때 밸런스 게임을 띄울지. 아직 안 한 사람만 true (건너뛰었거나 이미 했으면 false) */
        boolean balanceGameRequired
) {

    public static MyInfoResponse from(User user, StatsResponse stats) {
        return new MyInfoResponse(user.getId(), user.getNickname(),
                user.getEmail(), user.isGuest(), user.getProvider(), stats,
                PhilosopherSummary.of(user.getPhilosopherType()),
                user.getBalanceGameStatus() == BalanceGameStatus.NOT_STARTED);
    }

    /**
     * @param type  철학 유형 코드. 프로필 이미지는 프론트가 이 값으로 고름. 없으면 null
     * @param name  화면 이름 ("니체"). 유형이 없으면 "일반인"
     * @param alias 한 줄 별칭. 유형이 없으면 null
     */
    public record PhilosopherSummary(PhilosopherType type, String name, String alias) {

        public static PhilosopherSummary of(PhilosopherType type) {
            return type == null
                    ? new PhilosopherSummary(null, PhilosopherType.NONE_NAME, null)
                    : new PhilosopherSummary(type, type.getDisplayName(), type.getAlias());
        }
    }

    public record StatsResponse(int total, int wins, int losses, int draws) {

        public static StatsResponse empty() {
            return new StatsResponse(0, 0, 0, 0);
        }
    }
}
