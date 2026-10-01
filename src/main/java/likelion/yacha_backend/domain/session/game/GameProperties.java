package likelion.yacha_backend.domain.session.game;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml 의 {@code game.*}
 *
 * <p>명세 PART 5 에서 아직 정하지 않은 값이라 코드에 박지 않고 설정으로 뺐습니다.
 * 최종변론 100자는 명세에서 확정된 값이라 {@link Game#FINAL_MAX_LENGTH} 에 둡니다.
 *
 * <p>{@code int} 라 yaml 에 값이 없거나 키에 오타가 나면 0 으로 바인딩됩니다. 그러면 부팅은 성공하고
 * 토론이 시작된 뒤에야 모든 채팅이 거부되므로, {@code @Positive} 로 검증해 기동 단계에서 실패시킵니다.
 *
 * @param chatMaxLength          채팅 한 건의 글자 수 상한
 * @param maxChatsPerParticipant 참가자 한 명이 한 게임에서 보낼 수 있는 채팅 수 (최종변론 제외)
 */
@Validated
@ConfigurationProperties(prefix = "game")
public record GameProperties(
        @Positive int chatMaxLength,
        @Positive int maxChatsPerParticipant
) {
}
