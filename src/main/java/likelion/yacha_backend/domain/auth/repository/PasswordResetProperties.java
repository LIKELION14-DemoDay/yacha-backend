package likelion.yacha_backend.domain.auth.repository;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 {@code auth.password-reset.*}
 *
 * @param tokenTtl         인증번호 확인 뒤 받는 재설정 토큰의 유효시간.
 *                         새 비밀번호를 정하는 화면 하나만 남아서 짧게 둠 (10분)
 * @param sendInterval     같은 이메일로 다시 보낼 수 있을 때까지의 간격 (1분)
 * @param codeTtl          메일로 보낸 인증번호의 유효시간. 화면 타이머와 같음 (3분)
 * @param maxCodeAttempts  인증번호를 틀릴 수 있는 횟수. 넘기면 인증번호를 버려 다시 요청해야 함 (5번)
 */
@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetProperties(
        Duration tokenTtl,
        Duration sendInterval,
        Duration codeTtl,
        int maxCodeAttempts
) {
}
