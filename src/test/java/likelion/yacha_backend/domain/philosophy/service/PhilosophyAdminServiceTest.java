package likelion.yacha_backend.domain.philosophy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import likelion.yacha_backend.domain.philosophy.exception.PhilosophyErrorCode;
import likelion.yacha_backend.domain.philosophy.repository.PhilosophyArticleRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

/** 백그라운드 실행 부분. 나머지는 PhilosophyAdminApiTest */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("PhilosophyAdminService — 백그라운드 생성")
class PhilosophyAdminServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 11, 2);

    private final PhilosophyArticleRepository articleRepository = mock(PhilosophyArticleRepository.class);
    private final PhilosophyGenerationService generationService = mock(PhilosophyGenerationService.class);
    private final Clock clock = Clock.fixed(LocalDate.of(2026, 10, 29).atStartOfDay(KST).toInstant(), KST);

    private PhilosophyAdminService service(TaskExecutor executor) {
        return new PhilosophyAdminService(articleRepository, generationService,
                new PhilosophyGenerationProperties(5, 3, Duration.ZERO), executor, clock);
    }

    @Test
    @DisplayName("다른 생성이 돌고 있어 실행기가 거절하면 409 GENERATION_IN_PROGRESS")
    void rejectedWhileRunning() {
        TaskExecutor busy = task -> {
            throw new TaskRejectedException("busy");
        };

        assertThatThrownBy(() -> service(busy).startGenerate(NEXT_MONDAY))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(PhilosophyErrorCode.GENERATION_IN_PROGRESS));
        verifyNoInteractions(generationService);
    }

    @Test
    @DisplayName("요청에서는 확인 후 실행기에 넘기기만 하고, 생성은 실행기에서 돈다")
    void runsOnExecutor() {
        AtomicReference<Runnable> submitted = new AtomicReference<>();

        service(submitted::set).startGenerate(NEXT_MONDAY);

        verifyNoInteractions(generationService);
        assertThat(submitted.get()).isNotNull();
    }

    @Test
    @DisplayName("백그라운드에서 실패하면 예외를 밖으로 던지지 않고 에러 로그를 남긴다")
    void logsFailure(CapturedOutput output) {
        willThrow(new IllegalStateException("liner down")).given(generationService).generate(any());

        service(Runnable::run).startGenerate(NEXT_MONDAY);

        assertThat(output).contains("관리자 생성 weekStart=2026-11-02 실패");
    }

    @Test
    @DisplayName("다 찬 주면 실행기에 넘기지 않고 바로 409")
    void fullBeforeSubmit() {
        given(articleRepository.countByWeekStart(NEXT_MONDAY)).willReturn(5L);
        AtomicReference<Runnable> submitted = new AtomicReference<>();

        assertThatThrownBy(() -> service(submitted::set).startGenerate(NEXT_MONDAY))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(PhilosophyErrorCode.WEEK_ALREADY_FULL));
        assertThat(submitted.get()).isNull();
    }
}
