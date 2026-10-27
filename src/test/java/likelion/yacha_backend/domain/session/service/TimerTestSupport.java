package likelion.yacha_backend.domain.session.service;

import likelion.yacha_backend.domain.ai.BotWriter;
import likelion.yacha_backend.domain.ai.HintGenerator;
import likelion.yacha_backend.domain.ai.Judge;
import likelion.yacha_backend.domain.session.SessionFixture;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 실제 타이머로 확인하는 테스트(대기 타이머 · 구간 스케줄러)의 공통 설정.
 *
 * <p>설정 · 모의 빈이 같으면 스프링 테스트 컨텍스트를 함께 씁니다. 클래스마다 컨텍스트가 따로 뜨면
 * 컨텍스트마다 DB 연결 풀을 잡아, 로컬 MySQL 로 전체 테스트를 돌릴 때 연결 수 상한(151)을 넘습니다.
 *
 * <p>대기 타이머는 간격 1초 · 상한 3초 · 정리 간격 1초, 판정 · 힌트 재시도 대기는 10ms, 결과 보관 시간은 2초, 이탈 유예는 1초, 봇 대답 대기 300ms · 첫마디 대기 500ms · 재시도 대기 10ms(상한 50ms) · 대답 재시도 대기 200ms · 실패 뒤 무효 종료 대기 300ms 로 줄입니다. 타이머 작업은 커밋된 데이터를
 * 읽으므로 테스트 트랜잭션을 쓰지 않습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SessionFixture.class)
@TestPropertySource(properties = {"game.wait.prompt-interval=1s", "game.wait.limit=3s", "game.wait.sweep-interval=1s",
        "game.judge.retry-backoff=10ms", "game.judge.result-retention=2s", "game.hint.retry-backoff=10ms",
        "game.disconnect.grace=1s", "game.bot.reply-delay=300ms", "game.bot.first-message-delay=500ms",
        "game.bot.retry-backoff=10ms", "game.bot.retry-backoff-max=50ms", "game.bot.reply-retry-delay=200ms",
        "game.bot.failure-end-delay=300ms"})
abstract class TimerTestSupport {

    @MockitoBean
    protected MatchNotifier matchNotifier;

    @MockitoSpyBean
    protected GameMessageService gameMessageService;

    @MockitoSpyBean
    protected PhaseScheduler phaseScheduler;

    @MockitoSpyBean
    protected Judge judge;

    @MockitoSpyBean
    protected BotWriter botWriter;

    @MockitoSpyBean
    protected HintGenerator hintGenerator;

    @MockitoSpyBean
    protected WaitTimerService waitTimerService;
}
