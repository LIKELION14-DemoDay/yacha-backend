package likelion.yacha_backend.domain.session.game;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml 의 {@code game.*}
 *
 * <p>명세 PART 5 에서 아직 정하지 않은 값이라 코드에 박지 않고 설정으로 뺐습니다.
 * 최종변론 100자는 명세에서 확정된 값이라 {@link Game#FINAL_MAX_LENGTH} 에 둡니다.
 *
 * @param chatMaxLength 채팅 한 건의 글자 수 상한
 * @param maxMessages   게임 하나에 쌓을 수 있는 메시지 수 (채팅 + 최종변론)
 */
@ConfigurationProperties(prefix = "game")
public record GameProperties(int chatMaxLength, int maxMessages) {
}
