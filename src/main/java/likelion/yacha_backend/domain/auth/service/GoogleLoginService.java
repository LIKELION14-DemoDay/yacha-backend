package likelion.yacha_backend.domain.auth.service;

import likelion.yacha_backend.domain.auth.dto.GoogleCodeLoginRequest;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.dto.SocialLoginRequest;
import likelion.yacha_backend.domain.auth.social.GoogleTokenClient;
import likelion.yacha_backend.domain.user.entity.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 구글 인가 코드 로그인 (웹)
 *
 * 코드를 id_token으로 바꾼 뒤에는 {@link AuthService#socialLogin}에 그대로 넘김
 * 검증 · 가입 · 토큰 발급은 id_token을 바로 받는 경로와 완전히 같음
 *
 * AuthService 밖에 둔 이유는 {@link KakaoLoginService}와 같음
 *   구글 호출을 트랜잭션 밖에서 해야 응답을 기다리는 동안 DB 커넥션을 잡지 않음
 */
@Service
@RequiredArgsConstructor
public class GoogleLoginService {

    private final GoogleTokenClient googleTokenClient;
    private final AuthService authService;

    public IssuedTokens login(GoogleCodeLoginRequest request) {
        String idToken = googleTokenClient.exchangeForIdToken(request.code(), request.redirectUri());
        return authService.socialLogin(new SocialLoginRequest(Provider.GOOGLE, idToken));
    }
}
