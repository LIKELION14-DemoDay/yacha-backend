package likelion.yacha_backend.domain.philosophy.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PhilosophyGenerationProperties 검증")
class PhilosophyGenerationPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("기본값은 통과한다")
    void defaults() {
        assertThat(validator.validate(new PhilosophyGenerationProperties(5, 3, Duration.ofSeconds(2)))).isEmpty();
    }

    @Test
    @DisplayName("재시도 대기 0은 통과한다 (바로 다시 시도)")
    void zeroBackoff() {
        assertThat(validator.validate(new PhilosophyGenerationProperties(5, 3, Duration.ZERO))).isEmpty();
    }

    @Test
    @DisplayName("편수 · 시도 횟수가 0 이하이거나 대기가 음수 · 없음이면 실패한다")
    void invalid() {
        assertThat(validator.validate(new PhilosophyGenerationProperties(0, 3, Duration.ZERO))).isNotEmpty();
        assertThat(validator.validate(new PhilosophyGenerationProperties(5, 0, Duration.ZERO))).isNotEmpty();
        assertThat(validator.validate(new PhilosophyGenerationProperties(5, 3, Duration.ofSeconds(-1)))).isNotEmpty();
        assertThat(validator.validate(new PhilosophyGenerationProperties(5, 3, null))).isNotEmpty();
    }
}
