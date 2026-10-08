package likelion.yacha_backend.domain.auth.repository;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml의 {@code auth.password-reset.*}
 *
 * 값이 빠지거나 0 이하면 서버가 뜰 때 실패함
 * 그대로 뜨면 비밀번호 찾기가 조용히 망가짐
 *   maxCodeAttempts가 빠지면 0으로 바인딩돼 맞는 번호도 전부 만료로 처리됨
 *   codeTtl이 빠지면 saveCode 에서 NPE → 500
 *   기간이 0 이하면 Redis가 키를 바로 지우거나(PEXPIRE) 거부함(SET PX)
 *
 * @param tokenTtl         인증번호 확인 뒤 받는 재설정 토큰의 유효시간.
 *                         새 비밀번호를 정하는 화면 하나만 남아서 짧게 둠 (10분)
 * @param sendInterval     같은 이메일로 다시 보낼 수 있을 때까지의 간격 (1분)
 * @param codeTtl          메일로 보낸 인증번호의 유효시간. 화면 타이머와 같음 (3분)
 * @param maxCodeAttempts  인증번호를 틀릴 수 있는 횟수. 넘기면 인증번호를 버려 다시 요청해야 함 (5번)
 */
@Validated
@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetProperties(
        @NotNull Duration tokenTtl,
        @NotNull Duration sendInterval,
        @NotNull Duration codeTtl,
        @Positive int maxCodeAttempts
) {

    /** null은 위의 @NotNull이 검사함 */
    @AssertTrue(message = "auth.password-reset 의 기간 값은 0보다 커야 합니다.")
    public boolean isDurationsPositive() {
        return Stream.of(tokenTtl, sendInterval, codeTtl)
                .allMatch(duration -> duration == null || (!duration.isZero() && !duration.isNegative()));
    }
}
