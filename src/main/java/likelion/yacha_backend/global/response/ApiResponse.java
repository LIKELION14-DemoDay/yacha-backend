package likelion.yacha_backend.global.response;

import likelion.yacha_backend.global.exception.BaseErrorCode;
import org.slf4j.MDC;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        String traceId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, currentTraceId());
    }

    /** 데이터 없이 성공만 알리고 싶을 때 (예: DELETE). success(data)와 이름이 겹치면 record accessor와 충돌하므로 별도 이름 사용. */
    public static ApiResponse<Void> noContent() {
        return new ApiResponse<>(true, null, null, currentTraceId());
    }

    public static ApiResponse<Void> error(BaseErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(errorCode.name(), message), currentTraceId());
    }

    /** TraceIdFilter가 MDC에 넣어둔 값을 그대로 응답에 실어줍니다. */
    private static String currentTraceId() {
        return MDC.get("traceId");
    }

    public record ErrorDetail(String code, String message) {
    }
}
