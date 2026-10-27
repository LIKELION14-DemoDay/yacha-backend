
package likelion.yacha_backend.infra.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.infra.liner.LinerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Liner Model API의 Chat Completions를 호출하는 공통 클라이언트.
 *
 * 실제 요청은 Liner API로 전송한다.
 * 재시도 및 최종 실패 처리는 호출하는 서비스가 담당한다.
 */
public abstract class AbstractLinerChatClient {

    private static final String DEFAULT_BASE_URL =
            "https://platform.liner.com";

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(3);

    private static final Duration DEFAULT_READ_TIMEOUT =
            Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;

    /** 기존 호출부와 테스트에서 사용하는 생성자. */
    protected AbstractLinerChatClient(
            String apiKey,
            String model
    ) {
        this(apiKey, model, DEFAULT_READ_TIMEOUT);
    }

    /** LinerProperties 설정을 사용하는 생성자. */
    protected AbstractLinerChatClient(
            LinerProperties properties,
            Duration readTimeout
    ) {
        this(
                properties.baseUrl(),
                properties.apiKey(),
                properties.model(),
                readTimeout
        );
    }

    /** 기존 호출부와 테스트에서 사용하는 생성자. */
    protected AbstractLinerChatClient(
            String apiKey,
            String model,
            Duration readTimeout
    ) {
        this(DEFAULT_BASE_URL, apiKey, model, readTimeout);
    }

    /** 모든 생성자에서 공통으로 실행하는 초기화. */
    private AbstractLinerChatClient(
            String baseUrl,
            String apiKey,
            String model,
            Duration readTimeout
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Liner API 주소가 설정되지 않았습니다."
            );
        }

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException(
                    "Liner 모델이 설정되지 않았습니다."
            );
        }

        if (readTimeout == null
                || readTimeout.isZero()
                || readTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "readTimeout은 양수여야 합니다."
            );
        }

        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .connectTimeout(CONNECT_TIMEOUT)
                                .build()
                );

        factory.setReadTimeout(readTimeout);

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl.replaceFirst("/+$", "") + "/api/v1");

        if (enabled) {
            builder.defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + apiKey
            );
        }

        this.restClient = builder.build();
    }

    /**
     * Liner Model API에 채팅 생성 요청을 보낸다.
     *
     * @param systemPrompt AI 동작 지침
     * @param userContent AI에게 전달할 내용
     * @param logLabel 호출 종류 식별자
     * @return AI가 생성한 텍스트
     */
    protected String chat(
            String systemPrompt,
            String userContent,
            String logLabel
    ) {
        if (!enabled) {
            throw new LinerChatException(
                    "LINER_NOT_CONFIGURED: " + logLabel,
                    false
            );
        }

        try {
            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChatRequest(
                            model,
                            List.of(
                                    new ChatMessage(
                                            "system",
                                            systemPrompt
                                    ),
                                    new ChatMessage(
                                            "user",
                                            userContent
                                    )
                            )
                    ))
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null
                    || response.choices() == null
                    || response.choices().isEmpty()) {
                throw new LinerChatException(
                        "LINER_EMPTY_RESPONSE: " + logLabel,
                        false
                );
            }

            Choice firstChoice = response.choices().get(0);

            if (firstChoice == null
                    || firstChoice.message() == null) {
                throw new LinerChatException(
                        "LINER_EMPTY_MESSAGE: " + logLabel,
                        false
                );
            }

            String content = firstChoice.message().content();

            if (content == null || content.isBlank()) {
                throw new LinerChatException(
                        "LINER_EMPTY_CONTENT: " + logLabel,
                        false
                );
            }

            return content.trim();

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();

            // HTTP 429 및 5xx만 재시도 대상.
            boolean retryable = status == 429 || status >= 500;

            throw new LinerChatException(
                    "LINER_HTTP_ERROR_" + status + ": " + logLabel,
                    retryable,
                    e
            );

        } catch (ResourceAccessException e) {
            // 네트워크 장애 및 타임아웃은 재시도 대상.
            throw new LinerChatException(
                    "LINER_NETWORK_ERROR: " + logLabel,
                    true,
                    e
            );

        } catch (RestClientException e) {
            throw new LinerChatException(
                    "LINER_CLIENT_ERROR: " + logLabel,
                    false,
                    e
            );
        }
    }

    /** 재시도 가능 여부를 상위 AI 서비스에 전달하는 예외. */
    public static class LinerChatException extends AiException {

        public LinerChatException(
                String message,
                boolean retryable
        ) {
            super(message, retryable);
        }

        public LinerChatException(
                String message,
                boolean retryable,
                Throwable cause
        ) {
            super(message, retryable, cause);
        }
    }

    private record ChatRequest(
            String model,
            List<ChatMessage> messages
    ) {
    }

    private record ChatMessage(
            String role,
            String content
    ) {
    }

    private record ChatResponse(
            List<Choice> choices
    ) {
    }

    private record Choice(
            ChatMessage message
    ) {
    }
}
