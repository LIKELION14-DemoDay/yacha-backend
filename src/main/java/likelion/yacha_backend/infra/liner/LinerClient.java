package likelion.yacha_backend.infra.liner;

import java.net.http.HttpClient;
import java.time.Duration;

import likelion.yacha_backend.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class LinerClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final LinerProperties properties;
    private final RestClient restClient;

    @Autowired
    public LinerClient(LinerProperties properties) {
        this(
                properties,
                RestClient.builder()
                        .requestFactory(timeoutAwareRequestFactory())
                        .baseUrl(properties.baseUrl())
        );
    }

    /**
     * 테스트에서 MockRestServiceServer를 붙인 RestClient.Builder를
     * 주입할 수 있도록 분리한 생성자.
     */
    LinerClient(
            LinerProperties properties,
            RestClient.Builder builder
    ) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    /**
     * LINER Web Search API를 호출한다.
     *
     * @param query 검색할 문자열
     * @param maxResults 검색 결과 개수 (1~20)
     * @return LINER 검색 결과
     */
    public LinerSearchResponse searchWeb(
            String query,
            int maxResults
    ) {
        validateRequest(query, maxResults);

        if (!properties.isConfigured()) {
            throw new BusinessException(
                    LinerErrorCode.LINER_NOT_CONFIGURED
            );
        }

        LinerSearchRequest request = new LinerSearchRequest(
                query,
                "kr",
                "ko",
                null,
                maxResults
        );

        try {
            return restClient.post()
                    .uri("/api/v1/tools/search/web")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-api-key", properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(LinerSearchResponse.class);

        } catch (HttpClientErrorException e) {

            if (e.getStatusCode() == HttpStatus.PAYMENT_REQUIRED) {
                throw new BusinessException(
                        LinerErrorCode.LINER_CREDIT_EXHAUSTED,
                        e
                );
            }

            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException(
                        LinerErrorCode.LINER_RATE_LIMITED,
                        e
                );
            }

            log.error(
                    "LINER Web Search API가 요청을 거부했습니다. status={}, query={}",
                    e.getStatusCode(),
                    query,
                    e
            );

            throw new BusinessException(
                    LinerErrorCode.LINER_PROVIDER_ERROR,
                    e
            );

        } catch (HttpServerErrorException e) {

            log.error(
                    "LINER Web Search API 서버 오류입니다. status={}, query={}",
                    e.getStatusCode(),
                    query,
                    e
            );

            throw new BusinessException(
                    LinerErrorCode.LINER_PROVIDER_ERROR,
                    e
            );

        } catch (RestClientException e) {

            log.error(
                    "LINER Web Search API 통신에 실패했습니다. query={}",
                    query,
                    e
            );

            throw new BusinessException(
                    LinerErrorCode.LINER_PROVIDER_ERROR,
                    e
            );
        }
    }

    private void validateRequest(
            String query,
            int maxResults
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "LINER 검색어는 비어 있을 수 없습니다."
            );
        }

        if (maxResults < 1 || maxResults > 20) {
            throw new IllegalArgumentException(
                    "LINER maxResults는 1 이상 20 이하여야 합니다."
            );
        }
    }

    private static ClientHttpRequestFactory timeoutAwareRequestFactory() {
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .connectTimeout(CONNECT_TIMEOUT)
                                .build()
                );

        factory.setReadTimeout(READ_TIMEOUT);

        return factory;
    }
}