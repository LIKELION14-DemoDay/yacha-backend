package likelion.yacha_backend.domain.session.dto;

/** 방 생성 · 입장 응답. 프론트는 이 id 로 토론방을 구독합니다. */
public record SessionIdResponse(Long sessionId) {
}
