package likelion.yacha_backend.domain.session.dto;

/**
 * 주장 · 반론 제출 ({@code PUT /sessions/{id}/memo}). 지금 작성 구간의 내 글을 덮어씁니다. 빈 문자열도 제출로 받습니다.
 *
 * <p>글자 수 검사는 게임 객체가 구간 검사와 함께 합니다. <b>{@code content} 를 로그에 찍지 마세요</b> (명세 1-5).
 */
public record MemoSaveRequest(String content) {

    @Override
    public String toString() {
        return "MemoSaveRequest[length=" + (content == null ? 0 : content.length()) + "]";
    }
}
