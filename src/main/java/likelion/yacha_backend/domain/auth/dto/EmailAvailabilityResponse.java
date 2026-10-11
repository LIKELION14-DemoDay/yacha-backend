package likelion.yacha_backend.domain.auth.dto;

/** @param available 가입에 쓸 수 있으면 true, 이미 가입된 이메일이면 false */
public record EmailAvailabilityResponse(boolean available) {
}
