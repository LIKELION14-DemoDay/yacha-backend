package likelion.yacha_backend.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public BusinessException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(BaseErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 원인 예외를 함께 넘깁니다. <b>5xx 로 매핑되는 에러코드에는 반드시 이걸 쓰세요.</b>
     *
     * <p>원인을 버리면 서버에 남는 증거가 에러코드 한 줄뿐이라, 무엇이 실패했는지 알 방법이 없습니다.
     * {@code GlobalExceptionHandler} 가 5xx 일 때 스택 트레이스까지 남기는데,
     * 그러려면 원인이 여기 실려 있어야 합니다.
     */
    public BusinessException(BaseErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
