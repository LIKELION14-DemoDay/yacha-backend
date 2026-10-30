package likelion.yacha_backend.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import likelion.yacha_backend.domain.session.dto.SessionStateResponse;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.ParticipantType;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.service.PhilosopherImageUrlService;
import org.junit.jupiter.api.Test;

class SessionQueryServiceImageUrlTest {

    @Test
    void returnsPhilosopherDefaultAndBotImageUrls() {
        SessionAccessService access = mock(SessionAccessService.class);
        DebateParticipantRepository repo = mock(DebateParticipantRepository.class);
        GameRegistry games = mock(GameRegistry.class);
        PhilosopherImageUrlService images = new PhilosopherImageUrlService(
                "https://api.yachapan.cloud/api/v1/philosopher-images"
        );
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-30T06:00:00Z"), ZoneId.of("Asia/Seoul")
        );
        SessionQueryService service = new SessionQueryService(
                access, repo, games, clock, images
        );

        DebateSession session = mock(DebateSession.class);
        when(access.getSession(1L)).thenReturn(session);

        DebateParticipant kant = mock(DebateParticipant.class);
        User kantUser = mock(User.class);
        when(kant.getId()).thenReturn(10L);
        when(kant.getUser()).thenReturn(kantUser);
        when(kantUser.getPhilosopherType()).thenReturn(PhilosopherType.KANT);
        when(kant.getParticipantType()).thenReturn(ParticipantType.USER);

        DebateParticipant general = mock(DebateParticipant.class);
        User generalUser = mock(User.class);
        when(general.getId()).thenReturn(11L);
        when(general.getUser()).thenReturn(generalUser);
        when(general.getParticipantType()).thenReturn(ParticipantType.USER);

        DebateParticipant bot = mock(DebateParticipant.class);
        when(bot.getId()).thenReturn(12L);
        when(bot.isAi()).thenReturn(true);
        when(bot.getParticipantType()).thenReturn(ParticipantType.AI);

        DebateParticipant deleted = mock(DebateParticipant.class);
        when(deleted.getId()).thenReturn(13L);
        when(deleted.getParticipantType()).thenReturn(ParticipantType.USER);

        when(repo.findAllWithUserBySessionId(1L))
                .thenReturn(List.of(kant, general, bot, deleted));

        SessionStateResponse state = service.getState(1L, 99L);
        assertThat(state.participants())
                .extracting(SessionStateResponse.ParticipantResponse::imageUrl)
                .containsExactly(
                        images.readUrl(PhilosopherType.KANT),
                        images.readUrl(null),
                        images.botUrl(),
                        images.readUrl(null)
                );
    }
}
