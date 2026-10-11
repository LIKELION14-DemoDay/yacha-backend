package likelion.yacha_backend.domain.session.dto;

import jakarta.validation.constraints.NotNull;
import likelion.yacha_backend.domain.session.entity.Stance;

/**
 * 바로 봇전 ({@code POST /sessions/bot}).
 *
 * @param topicId 주제 화면에서 고른 주제. 방의 카테고리는 이 주제의 카테고리입니다
 * @param stance  내 입장. 봇은 반대 입장이 됩니다
 */
public record BotMatchRequest(
        @NotNull(message = "주제는 필수입니다.")
        Long topicId,

        @NotNull(message = "입장은 필수입니다.")
        Stance stance
) {
}
