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
 * 구글 토큰 API({@code POST https://oauth2.googleapis.com/token})로 인가 코드를 교환
 *
 * 응답에는 구글 access_token도 오지만 쓰지 않음
 * 로그인 뒤에는 우리 JWT만 쓰고, 구글 API 를 호출할 일이 없기 때문
 */
@Slf4j
@Component
public class GoogleRestTokenClient implements GoogleTokenClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** 사용자가 로그인 버튼을 누르고 기다리는 호출이라 짧게 둠 (KakaoRestTokenClient와 같음) */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    private final GoogleCodeProperties properties;
    private final RestClient restClient;

    @Autowired
    public GoogleRestTokenClient(GoogleCodeProperties properties) {
        this(properties, RestClient.builder().requestFactory(timeoutAwareRequestFactory()));
    }

    /** 테스트에서 MockRestServiceServer를 붙인 builder를 넣기 위한 생성자 */
    GoogleRestTokenClient(GoogleCodeProperties properties, RestClient.Builder builder) {
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
            // 클라이언트 ID · 시크릿이 없는 환경. /auth/social에서 설정 안 된 공급자와 같은 응답을 줌
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

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
            // 구글 5xx · 타임아웃 · 연결 실패
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_ERROR, e);
        }

        Object idToken = body == null ? null : body.get("id_token");
        if (!(idToken instanceof String token) || token.isBlank()) {
            // 프론트가 인가 요청의 scope에 openid를 넣지 않은 경우
            log.error("구글 토큰 응답에 id_token 이 없습니다. 인가 요청의 scope 에 openid 가 있는지 확인하세요.");
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_ERROR);
        }
        return token;
    }

    /**
     * 구글 4xx를 두 가지로 나눔
     *
     * invalid_grant: 코드 만료 · 이미 사용됨 · 잘못된 코드
     * redirect_uri_mismatch: 인가 요청 때와 다른 redirectUri를 보냄
     *   둘 다 요청 값 문제라 401. 다시 로그인하면 됨
     *   (등록 안 된 주소는 인가 단계에서 구글이 먼저 막으므로 여기까지 오지 않음)
     * 그 외(invalid_client 등): 클라이언트 ID · 시크릿이 틀린 서버 설정 문제
     *   사용자가 몇 번을 다시 해도 실패하므로 502로 올리고 error로 남김
     */
    private BusinessException clientError(HttpClientErrorException e) {
        String response = e.getResponseBodyAsString();
        if (response.contains("\"invalid_grant\"") || response.contains("\"redirect_uri_mismatch\"")) {
            log.warn("구글 인가 코드 교환 실패: {}", response);
            return new BusinessException(AuthErrorCode.INVALID_SOCIAL_CODE);
        }
        log.error("구글 토큰 요청이 거부됐습니다. 클라이언트 ID · 시크릿 설정을 확인하세요: status={}, body={}",
                e.getStatusCode(), response);
        return new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_ERROR, e);
    }
}
