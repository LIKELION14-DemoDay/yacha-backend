package likelion.yacha_backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import likelion.yacha_backend.domain.user.entity.Provider;

/**
 * 소셜 로그인 요청
 *
 * 프론트가 카카오 · 구글 SDK로 로그인해서 받은 {@code id_token}을 그대로 보냄
 * 서버는 이 토큰의 서명만 확인하므로 카카오 · 구글 API를 호출하지 않음
 *
 * @param provider 카카오 / 구글. {@code LOCAL} 을 보내면 400
 * @param idToken  소셜 SDK 가 준 id_token (액세스 토큰이 아님)
 */
public record SocialLoginRequest(

        @NotNull(message = "소셜 공급자는 필수입니다.")
        Provider provider,

        @NotBlank(message = "id_token 은 필수입니다.")
        String idToken
) {
}
