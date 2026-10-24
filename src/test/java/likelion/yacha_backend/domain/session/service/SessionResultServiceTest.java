package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.JudgeStatus;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 결과 조회가 판정 상태를 읽는 순간과 재판정을 시작하는 순간 사이에 다른 조회가 끼어드는 경우. */
@DisplayName("SessionResultService — FAILED 동시 조회")
class SessionResultServiceTest {

    private static final Long SESSION_ID = 1L;
    private static final Long USER_ID = 10L;

    private final SessionAccessService sessionAccessService = mock(SessionAccessService.class);
    private final DebateParticipantRepository participantRepository = mock(DebateParticipantRepository.class);
    private final GameRegistry gameRegistry = mock(GameRegistry.class);
    private final JudgeService judgeService = mock(JudgeService.class);
    private final Game game = mock(Game.class);
    private final SessionResultService service =
            new SessionResultService(sessionAccessService, participantRepository, gameRegistry, judgeService);

    @BeforeEach
    void setUp() {
        DebateSession session = mock(DebateSession.class);
        when(session.getStatus()).thenReturn(SessionStatus.FINISHED);
        when(session.getFinishReason()).thenReturn(FinishReason.COMPLETED);
        when(sessionAccessService.getSession(SESSION_ID)).thenReturn(session);
        DebateParticipant participant = mock(DebateParticipant.class);
        when(participant.isUser(USER_ID)).thenReturn(true);
        when(participantRepository.findAllBySession_Id(SESSION_ID)).thenReturn(List.of(participant));
        when(gameRegistry.find(SESSION_ID)).thenReturn(Optional.of(game));
        when(judgeService.start(SESSION_ID)).thenReturn(false);
    }

    @Test
    @DisplayName("FAILED 를 읽었지만 다른 조회가 먼저 재판정을 시작했으면 PENDING")
    void otherRequestRestarted() {
        when(game.judgeStatus()).thenReturn(JudgeStatus.FAILED, JudgeStatus.PENDING);

        assertThat(service.getResult(SESSION_ID, USER_ID).status()).isEqualTo(JudgeStatus.PENDING);
    }

    @Test
    @DisplayName("재판정을 시작하지 못했고 여전히 FAILED 면(횟수 소진) FAILED")
    void stillFailed() {
        when(game.judgeStatus()).thenReturn(JudgeStatus.FAILED);

        assertThat(service.getResult(SESSION_ID, USER_ID).status()).isEqualTo(JudgeStatus.FAILED);
    }
}
