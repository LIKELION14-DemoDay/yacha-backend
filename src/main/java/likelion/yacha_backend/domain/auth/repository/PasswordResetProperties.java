package likelion.yacha_backend.domain.auth.repository;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 {@code auth.password-reset.*}
 *
 * @param tokenTtl       재설정 링크 유효시간. 길면 메일함이 털렸을 때 위험 구간이 길어지고,
 *                       짧으면 사용자가 메일을 늦게 확인했을 때 다시 요청해야 함. (30분)
 * @param sendInterval   같은 이메일로 다시 보낼 수 있을 때까지의 간격 (1분)
 */
@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetProperties(Duration tokenTtl, Duration sendInterval) {
}
