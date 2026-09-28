package likelion.yacha_backend.domain.auth.social;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 토큰 API({@code POST /oauth/token})로 인가 코드를 교환
 *
 * 응답에는 카카오 access_token · refresh_token 도 오지만 쓰지 않음
 * 로그인 뒤에는 우리 JWT 만 쓰고, 카카오 API 를 호출할 일이 없기 때문
 */
@Slf4j
@Component
public class KakaoRestTokenClient implements KakaoTokenClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 응답 대기 상한. 없으면 무한 대기 (AbstractOpenAiChatClient 주석 참고)
     * 사용자가 로그인 버튼을 누르고 기다리는 호출이라 짧게 둠
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    private final KakaoCodeProperties properties;
    private final RestClient restClient;

    @Autowired
    public KakaoRestTokenClient(KakaoCodeProperties properties) {
        this(properties, RestClient.builder().requestFactory(timeoutAwareRequestFactory()));
    }

    /** 테스트에서 MockRestServiceServer 를 붙인 builder 를 넣기 위한 생성자 */
    KakaoRestTokenClient(KakaoCodeProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    private static ClientHttpRequestFactory timeoutAwareRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public String exchangeForIdToken(String code, String redirectUri) {
        if (!properties.isConfigured()) {
            // REST API 키가 없는 환경. /auth/social 에서 설정 안 된 공급자와 같은 응답을 줌
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (properties.hasClientSecret()) {
            form.add("client_secret", properties.clientSecret());
        }

        Map<String, Object> body;
        try {
            body = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JSON_OBJECT);
        } catch (HttpClientErrorException e) {
            throw clientError(e);
        } catch (RestClientException e) {
            // 카카오 5xx · 타임아웃 · 연결 실패
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_ERROR, e);
        }

        Object idToken = body == null ? null : body.get("id_token");
        if (!(idToken instanceof String token) || token.isBlank()) {
            // 콘솔에서 OpenID Connect 를 안 켰거나, 프론트가 scope 를 직접 넘기면서 openid 를 뺀 경우
            // 사용자가 고칠 수 없는 설정 문제라 error 로 남김
            log.error("카카오 토큰 응답에 id_token 이 없습니다. OpenID Connect 활성화와 openid scope 를 확인하세요.");
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
        }
        return token;
    }

    /**
     * 카카오 4xx 를 두 가지로 나눔
     *
     * invalid_grant: 코드 만료(10분) · 이미 사용됨 · redirect_uri 불일치
     *   사용자 쪽 문제라 401. 다시 로그인하면 됨
     * 그 외(invalid_client 등): REST API 키 · Client Secret 이 틀린 서버 설정 문제
     *   사용자가 몇 번을 다시 해도 실패하므로 502 로 올리고 error 로 남김
     */
    private BusinessException clientError(HttpClientErrorException e) {
        String response = e.getResponseBodyAsString();
        if (response.contains("\"invalid_grant\"")) {
            log.warn("카카오 인가 코드 교환 실패: {}", response);
            return new BusinessException(AuthErrorCode.INVALID_SOCIAL_CODE);
        }
        log.error("카카오 토큰 요청이 거부됐습니다. REST API 키 · Client Secret 설정을 확인하세요: status={}, body={}",
                e.getStatusCode(), response);
        return new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_ERROR, e);
    }
}
