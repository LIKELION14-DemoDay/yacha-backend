package likelion.yacha_backend.domain.ai;

/**
 * AI 호출 실패 (명세 2-9). 다시 시도해서 나아질 실패인지를 함께 알립니다.
 *
 * <p>타임아웃 · 네트워크 오류 · HTTP 429 · 5xx 는 다시 시도할 수 있고, 설정 누락 · 4xx(키 · 모델명 오류) 처럼
 * 다시 불러도 같은 결과인 실패는 다시 시도하지 않습니다. 구현체는 실패 종류를 알면 이 예외로 던지세요.
 * 이 예외가 아닌 실패는 부르는 쪽이 다시 시도할 수 있는 실패로 봅니다.
 */
public class AiException extends RuntimeException {

    private final boolean retryable;

    public AiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AiException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** 다시 시도해서 나아질 수 있는 실패면 true */
    public boolean isRetryable() {
        return retryable;
    }
}
