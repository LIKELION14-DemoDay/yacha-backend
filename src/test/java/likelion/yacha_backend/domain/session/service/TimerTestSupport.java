package likelion.yacha_backend.domain.session.service;

import likelion.yacha_backend.domain.session.SessionFixture;
import org.springframework.boot.test.context.SpringBootTest;
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
 * <p>대기 타이머는 간격 1초 · 상한 3초로 줄입니다. 타이머 작업은 커밋된 데이터를 읽으므로 테스트 트랜잭션을 쓰지 않습니다.
 */
@SpringBootTest
@Import(SessionFixture.class)
@TestPropertySource(properties = {"game.wait.prompt-interval=1s", "game.wait.limit=3s"})
abstract class TimerTestSupport {

    @MockitoBean
    protected MatchNotifier matchNotifier;

    @MockitoSpyBean
    protected GameMessageService gameMessageService;

    @MockitoSpyBean
    protected PhaseScheduler phaseScheduler;
}
