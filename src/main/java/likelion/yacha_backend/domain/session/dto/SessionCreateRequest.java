package likelion.yacha_backend.domain.session.dto;

import jakarta.validation.constraints.NotNull;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.Stance;

/**
 * 방 생성 ({@code POST /sessions}). 지금은 랜덤 방만 받습니다 — 친구 방은 구현 보류 (명세 2-3-3).
 *
 * @param topicId 주제 화면에서 받은 주제. 방의 카테고리는 이 주제의 카테고리입니다
 * @param stance  방장의 입장. 들어오는 사람은 반대 입장이 됩니다
 */
public record SessionCreateRequest(
        @NotNull(message = "방 종류는 필수입니다.")
        RoomType roomType,

        @NotNull(message = "주제는 필수입니다.")
        Long topicId,

        @NotNull(message = "입장은 필수입니다.")
        Stance stance
) {
}
