package likelion.yacha_backend.domain.auth.social;

import likelion.yacha_backend.domain.user.entity.Provider;

/**
 * 소셜 토큰에서 꺼낸 사용자 정보
 * 검증기가 공통 형태로 바꿔 서비스에 넘김
 *
 * @param provider      카카오 / 구글
 * @param providerId    소셜의 고유 id({@code sub}). <b>사용자 식별은 이 값으로만</b> 합니다
 * @param email         카카오는 이메일 제공이 선택 동의라 {@code null} 일 수 있습니다
 * @param emailVerified 소셜이 <b>확인한</b> 이메일인지. 기존 계정에 자동 연결할지 판단하는 근거입니다.
 *                      확인되지 않은 이메일로 연결하면, 남의 이메일을 적은 소셜 계정으로 그 사람
 *                      계정에 들어갈 수 있습니다
 * @param nickname      소셜 프로필 이름. 없으면 서버가 게스트처럼 만들어 줍니다
 */
public record SocialProfile(
        Provider provider,
        String providerId,
        String email,
        boolean emailVerified,
        String nickname
) {

    public boolean hasVerifiedEmail() {
        return email != null && !email.isBlank() && emailVerified;
    }
}
