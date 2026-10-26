package likelion.yacha_backend.domain.philosophy.dto;

/**
 * 저장된 메모
 *
 * @param memo 저장된 내용 (앞뒤 공백을 지운 것). 지웠으면 null
 */
public record PhilosophyMemoResponse(String memo) {
}
