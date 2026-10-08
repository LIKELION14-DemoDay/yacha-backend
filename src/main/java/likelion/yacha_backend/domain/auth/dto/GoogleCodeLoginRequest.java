package likelion.yacha_backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 구글 로그인 요청 (웹)
 *
 * 프론트가 구글 인가 주소로 보낸 뒤 redirect URI로 돌아온 인가 코드를 보냄
 * 서버가 이 코드를 구글에 보내 id_token으로 바꾼 뒤, {@code /auth/social}과 같은 절차로 로그인함
 *
 * @param code        redirect URI의 {@code ?code=} 값. 한 번만 쓸 수 있음
 * @param redirectUri 구글 인가 요청에 넣은 값과 똑같아야 함
 */
public record GoogleCodeLoginRequest(

        @NotBlank(message = "인가 코드는 필수입니다.")
        String code,

        @NotBlank(message = "redirectUri 는 필수입니다.")
        String redirectUri
) {
}
