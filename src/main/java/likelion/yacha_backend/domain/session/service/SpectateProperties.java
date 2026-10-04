package likelion.yacha_backend.domain.session.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml 의 {@code spectate.*} — 관전 스위치.
 *
 * <p>관전은 10/5 결정으로 <b>사용하지 않습니다.</b> 관전 규칙({@link SessionAccessService#isSpectatable})은
 * 지우지 않고 남겨 두고, 이 값으로 끕니다. 키가 없으면 {@code false} 라 실수로 관전이 열리지 않습니다.
 *
 * @param enabled true 면 진행 중인 랜덤 사람전을 참가자가 아니어도 볼 수 있습니다 (관전)
 */
@ConfigurationProperties(prefix = "spectate")
public record SpectateProperties(boolean enabled) {
}
