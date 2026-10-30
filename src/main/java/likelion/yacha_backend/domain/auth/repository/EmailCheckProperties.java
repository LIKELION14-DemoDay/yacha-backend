package likelion.yacha_backend.domain.auth.repository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml의 {@code auth.email-check.*}
 *
 * 회원가입 이메일 중복 확인의 호출 제한. 이메일 목록을 대량으로 대조하는 데 쓰이지 않게 막음
 * 값이 빠지거나 잘못되면 서버가 뜰 때 실패함. 그대로 뜨면 제한이 조용히 꺼질 수 있어서
 *
 * @param ipLimit 한 IP가 부를 수 있는 횟수 (1분에 30번)
 */
@Validated
@ConfigurationProperties(prefix = "auth.email-check")
public record EmailCheckProperties(
        @NotNull @Valid IpRateLimit ipLimit
) {
}
