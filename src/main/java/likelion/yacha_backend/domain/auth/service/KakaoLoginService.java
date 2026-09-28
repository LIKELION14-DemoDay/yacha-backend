package likelion.yacha_backend.domain.auth.service;

import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.dto.KakaoCodeLoginRequest;
import likelion.yacha_backend.domain.auth.dto.SocialLoginRequest;
import likelion.yacha_backend.domain.auth.social.KakaoTokenClient;
import likelion.yacha_backend.domain.user.entity.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 카카오 인가 코드 로그인 (웹)
 *
 * 코드를 id_token 으로 바꾼 뒤에는 {@link AuthService#socialLogin} 에 그대로 넘김
 * 검증 · 가입 · 토큰 발급은 SDK 로 id_token 을 받는 경로와 완전히 같음
 *
 * AuthService 안에 두지 않은 이유
 *   카카오 호출은 트랜잭션 밖에서 해야 함. 응답을 기다리는 동안 DB 커넥션을 잡고 있으면 안 되기 때문
 *   같은 클래스 안에서 부르면 socialLogin 의 @Transactional 이 적용되지 않아서 클래스를 나눔
 */
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private final KakaoTokenClient kakaoTokenClient;
    private final AuthService authService;

    public IssuedTokens login(KakaoCodeLoginRequest request) {
        String idToken = kakaoTokenClient.exchangeForIdToken(request.code(), request.redirectUri());
        return authService.socialLogin(new SocialLoginRequest(Provider.KAKAO, idToken));
    }
}
