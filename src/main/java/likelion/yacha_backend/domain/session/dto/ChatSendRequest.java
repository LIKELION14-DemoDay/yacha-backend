package likelion.yacha_backend.domain.session.dto;

/**
 * 채팅 전송 ({@code SEND /app/sessions/{id}/chat}).
 *
 * <p>비어 있음 · 글자 수 검사는 게임 객체가 구간 검사와 함께 합니다.
 * <b>{@code content} 를 로그에 찍지 마세요</b> (명세 1-5).
 */
public record ChatSendRequest(String content) {

    @Override
    public String toString() {
        return "ChatSendRequest[length=" + (content == null ? 0 : content.length()) + "]";
    }
}
