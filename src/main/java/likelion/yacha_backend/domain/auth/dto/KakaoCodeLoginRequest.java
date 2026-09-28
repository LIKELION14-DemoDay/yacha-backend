package likelion.yacha_backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 카카오 로그인 요청 (웹)
 *
 * 카카오 JS SDK 의 {@code Kakao.Auth.authorize()} 가 redirect URI 로 넘겨준 인가 코드를 보냄
 * 서버가 이 코드를 카카오에 보내 id_token 으로 바꾼 뒤, {@code /auth/social} 과 같은 절차로 로그인함
 *
 * @param code        redirect URI 의 {@code ?code=} 값. 10분 안에 한 번만 쓸 수 있음
 * @param redirectUri {@code Kakao.Auth.authorize()} 에 넣은 값과 똑같아야 함
 */
public record KakaoCodeLoginRequest(

        @NotBlank(message = "인가 코드는 필수입니다.")
        String code,

        @NotBlank(message = "redirectUri 는 필수입니다.")
        String redirectUri
) {
}
