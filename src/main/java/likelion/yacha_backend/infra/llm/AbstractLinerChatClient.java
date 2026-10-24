
package likelion.yacha_backend.infra.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

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
 * 기존 코드 및 테스트와의 호환성을 위해 클래스 이름은 유지한다.
 * 실제 요청은 OpenAI가 아닌 Liner API로 전송한다.
 *
 * 재시도 및 최종 실패 처리는 호출하는 서비스가 담당한다.
 */
public abstract class AbstractLinerChatClient {

    private static final String BASE_URL =
            "https://platform.liner.com/api/v1";

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(3);

    private static final Duration DEFAULT_READ_TIMEOUT =
            Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;

    protected AbstractLinerChatClient(
            String apiKey,
            String model
    ) {
        this(apiKey, model, DEFAULT_READ_TIMEOUT);
    }

    protected AbstractLinerChatClient(
            String apiKey,
            String model,
            Duration readTimeout
    ) {
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
                .baseUrl(BASE_URL);

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

            // 타임아웃 외에 HTTP 429 및 5xx만 재시도 대상.
            boolean retryable =
                    status == 429 || status >= 500;

            throw new LinerChatException(
                    "LINER_HTTP_ERROR_" + status
                            + ": " + logLabel,
                    retryable,
                    e
            );

        } catch (ResourceAccessException e) {
            // 연결 실패 및 네트워크 타임아웃.
            throw new LinerChatException(
                    "LINER_NETWORK_ERROR: " + logLabel,
                    true,
                    e
            );

        } catch (RestClientException e) {
            // 응답 변환 오류 등 기타 클라이언트 예외.
            throw new LinerChatException(
                    "LINER_CLIENT_ERROR: " + logLabel,
                    false,
                    e
            );
        }
    }

    /**
     * Liner 호출 실패를 표현하는 예외.
     *
     * 기존 테스트 호환성을 위해 예외 클래스 이름은 유지한다.
     */
    public static class LinerChatException
            extends RuntimeException {

        private final boolean retryable;

        public LinerChatException(
                String message,
                boolean retryable
        ) {
            super(message);
            this.retryable = retryable;
        }

        public LinerChatException(
                String message,
                boolean retryable,
                Throwable cause
        ) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    /**
     * Liner Chat Completions 요청 형식.
     */
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

    /**
     * Liner Chat Completions 응답 형식.
     */
    private record ChatResponse(
            List<Choice> choices
    ) {
    }

    private record Choice(
            ChatMessage message
    ) {
    }
}
