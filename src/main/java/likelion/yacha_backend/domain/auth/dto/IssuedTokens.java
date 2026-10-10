package likelion.yacha_backend.domain.auth.dto;

import likelion.yacha_backend.domain.user.entity.User;

/**
 * 서비스가 컨트롤러에게 돌려주는 내부 전달용 값
 *
 * @param newUser 이번 요청으로 계정이 새로 만들어졌는지. 프론트가 소셜 첫 로그인에 닉네임 화면을 띄울 때 씀
 */
public record IssuedTokens(String accessToken, String refreshToken, User user, boolean newUser) {

    /** 기존 계정으로 발급한 경우. 로그인 · 재발급 · 승격 · 비밀번호 변경 */
    public IssuedTokens(String accessToken, String refreshToken, User user) {
        this(accessToken, refreshToken, user, false);
    }

    /** 이번 요청으로 만든 계정이라고 표시. 게스트 생성 · 회원가입 · 소셜 첫 로그인 */
    public IssuedTokens asNewUser() {
        return new IssuedTokens(accessToken, refreshToken, user, true);
    }

    public AuthResponse toResponse() {
        return AuthResponse.from(accessToken, user, newUser);
    }
}
