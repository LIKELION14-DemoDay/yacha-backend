package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.session.game.GameTimers;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

@DisplayName("대기 팝업 시각 — 첫 알림 10초 뒤 방을 만든 시각 기준 30초마다")
class WaitPromptTimesTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 12, 15, 0);

    private WaitTimerService service(Duration first, Duration interval, Duration limit) {
        return new WaitTimerService(mock(GameTimers.class), mock(DebateSessionRepository.class),
                mock(MatchNotifier.class), mock(PlatformTransactionManager.class),
                new WaitTimerProperties(first, interval, limit, Duration.ofMinutes(1)), Clock.systemDefaultZone());
    }

    private List<Long> waitedSeconds(WaitTimerService service) {
        return service.promptTimes(CREATED_AT).stream()
                .map(at -> Duration.between(CREATED_AT, at).toSeconds())
                .toList();
    }

    @Test
    @DisplayName("운영 설정(10초 · 30초 · 5분)이면 10, 30, 60 … 270초에 보낸다")
    void production() {
        WaitTimerService service = service(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(5));

        assertThat(waitedSeconds(service))
                .containsExactly(10L, 30L, 60L, 90L, 120L, 150L, 180L, 210L, 240L, 270L);
    }

    @Test
    @DisplayName("첫 알림이 간격과 같으면 한 번만 넣는다")
    void firstEqualsInterval() {
        WaitTimerService service = service(Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(100));

        assertThat(waitedSeconds(service)).containsExactly(30L, 60L, 90L);
    }

    @Test
    @DisplayName("상한 시각과 같거나 지난 팝업은 넣지 않는다")
    void beforeLimitOnly() {
        WaitTimerService service = service(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(60));

        assertThat(waitedSeconds(service)).containsExactly(10L, 30L);
    }
}
