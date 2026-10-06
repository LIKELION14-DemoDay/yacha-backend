package likelion.yacha_backend.domain.auth.repository;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 {@code auth.password-reset.*}
 *
 * @param tokenTtl         재설정 링크 유효시간. 길면 메일함이 털렸을 때 위험 구간이 길어지고,
 *                         짧으면 사용자가 메일을 늦게 확인했을 때 다시 요청해야 함. (30분)
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
