package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.repository.DebateSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@DisplayName("InProgressSessionCleaner — 정리 실패가 기동을 막지 않음")
class InProgressSessionCleanerFailureTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime BOOT_TIME = LocalDateTime.now(CLOCK);

    private final DebateSessionRepository sessionRepository = mock(DebateSessionRepository.class);
    private final InProgressSessionCleaner cleaner =
            new InProgressSessionCleaner(sessionRepository, new NoOpTransactionManager(), CLOCK);

    private static DebateSession session(Long id) {
        DebateSession session = mock(DebateSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    @Test
    @DisplayName("세션 하나가 실패해도 나머지 세션은 정리한다")
    void continuesAfterFailedSession() {
        List<DebateSession> sessions = List.of(session(1L), session(2L), session(3L));
        when(sessionRepository.findAllByStatusAndStartedAtBefore(SessionStatus.IN_PROGRESS, BOOT_TIME))
                .thenReturn(sessions);
        when(sessionRepository.finishIfInProgress(eq(1L), eq(FinishReason.ABORTED), any())).thenReturn(1);
        when(sessionRepository.finishIfInProgress(eq(2L), eq(FinishReason.ABORTED), any()))
                .thenThrow(new QueryTimeoutException("잠금 대기 시간 초과"));
        when(sessionRepository.finishIfInProgress(eq(3L), eq(FinishReason.ABORTED), any())).thenReturn(1);

        int aborted = cleaner.abortInProgressSessions(BOOT_TIME);

        assertThat(aborted).isEqualTo(2);
        verify(sessionRepository).finishIfInProgress(eq(3L), eq(FinishReason.ABORTED), any());
    }

    @Test
    @DisplayName("진행 중 세션 조회가 실패해도 기동 이벤트에서 예외를 던지지 않는다")
    void readyEventSwallowsFailure() {
        when(sessionRepository.findAllByStatusAndStartedAtBefore(any(), any()))
                .thenThrow(new QueryTimeoutException("DB 응답 없음"));

        assertThatCode(cleaner::onApplicationReady).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("기동 이벤트는 빈을 만든 시각보다 먼저 시작된 게임만 정리한다")
    void readyEventUsesBootTime() {
        cleaner.onApplicationReady();

        verify(sessionRepository).findAllByStatusAndStartedAtBefore(SessionStatus.IN_PROGRESS, BOOT_TIME);
    }

    /** 트랜잭션 경계만 흉내 냄. 저장소가 목이라 실제 트랜잭션은 필요 없음 */
    private static final class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
