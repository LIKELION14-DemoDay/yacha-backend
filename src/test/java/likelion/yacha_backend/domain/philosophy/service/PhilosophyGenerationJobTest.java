package likelion.yacha_backend.domain.philosophy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;
import likelion.yacha_backend.domain.philosophy.exception.PhilosophyErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("PhilosophyGenerationJob")
class PhilosophyGenerationJobTest {

    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 11, 2);

    private final PhilosophyGenerationService service = mock(PhilosophyGenerationService.class);
    private final PhilosophyGenerationJob job = new PhilosophyGenerationJob(service);

    @BeforeEach
    void setUp() {
        given(service.nextWeekStart()).willReturn(NEXT_MONDAY);
    }

    @Test
    @DisplayName("다음 주 글을 만들고 검토하라는 로그를 남긴다")
    void generatesNextWeek(CapturedOutput output) {
        given(service.generate(NEXT_MONDAY)).willReturn(new PhilosophyGenerationResult(List.of(1L, 2L), 0));

        job.run();

        assertThat(output).contains("2026-11-02 주 글을 2편 만들었습니다. 검토 후 승인해 주세요. ids=[1, 2]");
    }

    @Test
    @DisplayName("일부가 실패하면 모자란 만큼 생성하라는 에러 로그를 남긴다")
    void logsPartialFailure(CapturedOutput output) {
        given(service.generate(NEXT_MONDAY)).willReturn(new PhilosophyGenerationResult(List.of(1L), 4));

        job.run();

        assertThat(output).contains("1편 만들고 4편은 만들지 못했습니다. 관리자 API로 모자란 만큼 생성해 주세요");
    }

    @Test
    @DisplayName("이미 다 있으면 넘어간다")
    void skipsWhenFull(CapturedOutput output) {
        given(service.generate(NEXT_MONDAY)).willThrow(new BusinessException(PhilosophyErrorCode.WEEK_ALREADY_FULL));

        assertThatCode(job::run).doesNotThrowAnyException();
        assertThat(output).contains("이미 다 있어 만들지 않았습니다");
    }

    @Test
    @DisplayName("생성 중 예외가 나도 밖으로 던지지 않고 에러 로그를 남긴다")
    void logsFailure(CapturedOutput output) {
        given(service.generate(NEXT_MONDAY)).willThrow(new IllegalStateException("db down"));

        assertThatCode(job::run).doesNotThrowAnyException();
        assertThat(output).contains("관리자 API로 다시 만들어 주세요");
    }
}
