package likelion.yacha_backend.domain.session.dto;

/**
 * 주장 저장 ({@code PUT /sessions/{id}/memo}). 현재 작성 구간의 내 주장을 덮어씁니다. 비우려면 빈 문자열을 보냅니다.
 *
 * <p>글자 수 검사는 게임 객체가 구간 검사와 함께 합니다. <b>{@code content} 를 로그에 찍지 마세요</b> (명세 1-5).
 */
public record MemoSaveRequest(String content) {

    @Override
    public String toString() {
        return "MemoSaveRequest[length=" + (content == null ? 0 : content.length()) + "]";
    }
}
