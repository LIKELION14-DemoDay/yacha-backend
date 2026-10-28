package likelion.yacha_backend.domain.philosophy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophyMemo;

/**
 * 메모 저장. 앞뒤 공백은 지우고 저장하고, 비어 있으면 메모를 지움
 *
 * @param content 메모 내용 (1000자 이하)
 */
public record PhilosophyMemoRequest(

        @NotNull(message = "메모 내용은 필수입니다. 지우려면 빈 문자열을 보내 주세요.")
        @Size(max = PhilosophyMemo.CONTENT_MAX_LENGTH, message = "메모는 1000자 이하여야 합니다.")
        String content
) {
}
