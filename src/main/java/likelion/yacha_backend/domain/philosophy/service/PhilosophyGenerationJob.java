package likelion.yacha_backend.domain.philosophy.service;

import java.time.LocalDate;
import likelion.yacha_backend.domain.philosophy.exception.PhilosophyErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매주 다음 주 1분 철학 글을 만들어 둠. 기본은 목요일 오전 10시
 * 금요일 · 주말에 팀원이 검토 · 승인하면 월요일에 한꺼번에 보임
 *
 * 실패해도 다시 돌지 않음. 로그를 보고 관리자 API로 모자란 만큼 생성하면 됨
 * 다음 주 글이 하나도 승인되지 않으면 지난주 글이 계속 보이므로 화면이 비지는 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhilosophyGenerationJob {

    private final PhilosophyGenerationService generationService;

    @Scheduled(cron = "${philosophy.generation.cron}")
    public void run() {
        LocalDate weekStart = generationService.nextWeekStart();
        try {
            PhilosophyGenerationResult result = generationService.generate(weekStart);
            if (result.failed() > 0) {
                log.error("[1분 철학] {} 주 글을 {}편 만들고 {}편은 만들지 못했습니다. 관리자 API로 모자란 만큼 생성해 주세요. ids={}",
                        weekStart, result.createdIds().size(), result.failed(), result.createdIds());
                return;
            }
            log.info("[1분 철학] {} 주 글을 {}편 만들었습니다. 검토 후 승인해 주세요. ids={}",
                    weekStart, result.createdIds().size(), result.createdIds());
        } catch (BusinessException e) {
            if (e.getErrorCode() == PhilosophyErrorCode.WEEK_ALREADY_FULL) {
                log.info("[1분 철학] {} 주 글이 이미 다 있어 만들지 않았습니다", weekStart);
                return;
            }
            log.error("[1분 철학] {} 주 글을 만들지 못했습니다. code={}", weekStart, e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("[1분 철학] {} 주 글을 만들지 못했습니다. 관리자 API로 다시 만들어 주세요", weekStart, e);
        }
    }
}
