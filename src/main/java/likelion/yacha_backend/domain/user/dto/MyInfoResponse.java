
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
        Provider provider,
        StatsResponse stats,
        PhilosopherSummary philosopher,
        boolean balanceGameRequired
) {

    public static MyInfoResponse from(
            User user,
            StatsResponse stats,
            String imageUrl
    ) {
        return new MyInfoResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.isGuest(),
                user.getProvider(),
                stats,
                PhilosopherSummary.of(
                        user.getPhilosopherType(),
                        imageUrl
                ),
                user.getBalanceGameStatus()
                        == BalanceGameStatus.NOT_STARTED
        );
    }

    /**
     * 철학자 유형과 이미지 URL
     */
    public record PhilosopherSummary(
            PhilosopherType type,
            String name,
            String alias,
            String imageUrl
    ) {

        public static PhilosopherSummary of(
                PhilosopherType type,
                String imageUrl
        ) {
            return type == null
                    ? new PhilosopherSummary(
                    null,
                    PhilosopherType.NONE_NAME,
                    null,
                    imageUrl
            )
                    : new PhilosopherSummary(
                    type,
                    type.getDisplayName(),
                    type.getAlias(),
                    imageUrl
            );
        }
    }

    public record StatsResponse(long wins, long losses) {
    }
}
