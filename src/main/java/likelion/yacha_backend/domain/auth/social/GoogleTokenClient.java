package likelion.yacha_backend.domain.auth.social;

/**
 * 구글 인가 코드를 id_token으로 바꿈
 *
 * 받은 id_token은 다른 경로와 똑같이 {@link GoogleTokenVerifier}로 검증함
 * 여기서는 교환만 하고 토큰 내용은 믿지 않음
 */
public interface GoogleTokenClient {

    /**
     * @param code        구글이 redirect URI로 넘겨준 인가 코드. 한 번만 쓸 수 있음
     * @param redirectUri 인가 요청 때 쓴 값과 글자 하나까지 같아야 함 (구글이 비교함)
     * @return 서명 검증 전의 id_token
     */
    String exchangeForIdToken(String code, String redirectUri);
}
