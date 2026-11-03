
package likelion.yacha_backend.domain.session.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import likelion.yacha_backend.domain.session.entity.RoomType;
import likelion.yacha_backend.domain.session.entity.Stance;
import likelion.yacha_backend.domain.topic.entity.Category;

public record SessionCreateRequest(

        @NotNull(message = "방 종류는 필수입니다.")
        RoomType roomType,

        Long topicId,

        Stance stance,

        Category category
) {

    // 기존 코드 및 테스트의 3개 인자 생성자 호환
    public SessionCreateRequest(
            RoomType roomType,
            Long topicId,
            Stance stance
    ) {
        this(roomType, topicId, stance, null);
    }

    @JsonIgnore
    @AssertTrue(message = "방 종류에 맞는 필수 정보를 입력해야 합니다.")
    public boolean isValidCombination() {
        if (roomType == null) {
            return true; // @NotNull에서 검증
        }

        if (roomType == RoomType.RANDOM) {
            return topicId != null
                    && stance != null
                    && category == null;
        }

        if (roomType == RoomType.FRIEND) {
            return category != null
                    && topicId == null
                    && stance == null;
        }

        return false;
    }
}
