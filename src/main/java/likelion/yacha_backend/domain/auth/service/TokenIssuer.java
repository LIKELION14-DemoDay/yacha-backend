package likelion.yacha_backend.domain.auth.service;

import java.util.Optional;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.repository.RefreshTokenStore;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 로그인에 성공했다는 사실을 토큰 두개로 바꾸는 한 곳
 * 게스트 발급 · 회원가입 · 로그인 · 재발급 · 승격
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    /** 액세스·리프레시 토큰을 발급하고 리프레시 토큰을 저장소에 기록 */
    public IssuedTokens issue(User user) {
        // role 은 DB 의 현재 값을 그대로
        // 권한이 바뀌어도 늦어도 액세스 토큰 수명(10분) 안에는 반영
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());

        // 리프레시 토큰에는 role 을 싣지 않음
        // 인가 판단은 액세스 토큰
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenStore.save(user.getId(), refreshToken);

        return new IssuedTokens(accessToken, refreshToken, user);
    }

    /** 저장된 리프레시 토큰을 지움. 로그아웃·재사용 탐지에서 사용 */
    public void revoke(Long userId) {
        refreshTokenStore.delete(userId);
    }

    /**
     * 지금 유효한 리프레시 토큰
     * 재발급에서 쿠키로 온 값이 저장된 값과 같은지 확인할 때 사용
     */
    public Optional<String> findStoredRefreshToken(Long userId) {
        return refreshTokenStore.find(userId);
    }
}
