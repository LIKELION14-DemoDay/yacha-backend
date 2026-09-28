package likelion.yacha_backend.domain.auth.social;

/**
 * 카카오 인가 코드를 id_token 으로 바꿈
 *
 * 받은 id_token 은 다른 경로와 똑같이 {@link KakaoTokenVerifier} 로 검증함
 * 여기서는 교환만 하고 토큰 내용은 믿지 않음
 */
public interface KakaoTokenClient {

    /**
     * @param code        카카오가 redirect URI 로 넘겨준 인가 코드. 한 번만 쓸 수 있음
     * @param redirectUri 인가 요청 때 쓴 값과 글자 하나까지 같아야 함 (카카오가 비교함)
     * @return 서명 검증 전의 id_token
     */
    String exchangeForIdToken(String code, String redirectUri);
}
