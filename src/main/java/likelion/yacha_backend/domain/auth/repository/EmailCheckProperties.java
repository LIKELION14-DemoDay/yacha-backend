package likelion.yacha_backend.domain.auth.repository;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml의 {@code auth.email-check.*}
 *
 * 회원가입 이메일 중복 확인의 호출 제한. 이메일 목록을 대량으로 대조하는 데 쓰이지 않게 막음
 *
 * @param maxRequests 한 IP가 {@code window} 안에 부를 수 있는 횟수
 * @param window      횟수를 세는 구간. 처음 부른 때부터 이만큼 지나면 0부터 다시 셈
 */
@Validated
@ConfigurationProperties(prefix = "auth.email-check")
public record EmailCheckProperties(
        @Positive int maxRequests,
        @NotNull Duration window
) {

    /**
     * 0이나 음수면 Redis가 PEXPIRE에서 키를 바로 지워 횟수가 매번 1부터 시작함 = 제한이 꺼짐
     * 잘못된 설정으로 제한이 조용히 꺼지지 않게 서버가 뜰 때 막음 (null 은 위의 @NotNull 이 검사)
     */
    @AssertTrue(message = "auth.email-check.window 는 0보다 커야 합니다.")
    public boolean isWindowPositive() {
        return window == null || (!window.isZero() && !window.isNegative());
    }
}
