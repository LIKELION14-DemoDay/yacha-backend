package likelion.yacha_backend.domain.session.service;

import likelion.yacha_backend.domain.session.dto.BotMatchRequest;
import likelion.yacha_backend.domain.session.dto.SessionCreateRequest;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.service.SessionCommandService.Matched;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 방 생성 · 입장 · 취소 · 봇전 시작의 진입점. DB 작업은 {@link SessionCommandService} 의 트랜잭션에서 하고,
 * <b>커밋이 끝난 뒤에</b> 게임 생성과 방장 알림을 합니다.
 *
 * <p>트랜잭션 안에서 하면 두 가지 문제가 생깁니다.
 * <ul>
 *   <li>커밋 전에 {@code MATCHED} 를 받은 프론트가 {@code /state} 를 부르면 아직 {@code WAITING} 이 보입니다.</li>
 *   <li>커밋이 실패해 롤백되면 메모리에 게임만 남습니다.</li>
 * </ul>
 * 그래서 이 클래스에는 트랜잭션을 걸지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class SessionMatchFacade {

    private final SessionCommandService sessionCommandService;
    private final GameRegistry gameRegistry;
    private final MatchNotifier matchNotifier;

    public Long create(Long userId, SessionCreateRequest request) {
        return sessionCommandService.create(userId, request);
    }

    /** 입장 → (커밋) → 게임 생성 → 방장에게 {@code MATCHED}. 입장한 사람은 응답으로 시작을 압니다. */
    public Long join(Long userId, Long sessionId) {
        Matched matched = sessionCommandService.join(userId, sessionId);
        createGame(matched);
        matchNotifier.matched(matched.hostUserId(), matched.sessionId());
        return matched.sessionId();
    }

    /** 바로 봇전 → (커밋) → 게임 생성. 사람이 한 명뿐이라 {@code MATCHED} 는 보내지 않고 응답으로 시작을 알립니다. */
    public Long startBot(Long userId, BotMatchRequest request) {
        Matched matched = sessionCommandService.startBot(userId, request);
        createGame(matched);
        return matched.sessionId();
    }

    /** 대기 중 AI 전환 → (커밋) → 게임 생성. 전환을 요청한 방장이 응답으로 시작을 압니다. */
    public Long convertToAi(Long userId, Long sessionId) {
        Matched matched = sessionCommandService.convertToAi(userId, sessionId);
        createGame(matched);
        return matched.sessionId();
    }

    public void cancel(Long userId, Long sessionId) {
        sessionCommandService.cancel(userId, sessionId);
    }

    private void createGame(Matched matched) {
        gameRegistry.create(matched.sessionId(), matched.startedAt(), matched.participantIdByUserId(),
                matched.aiParticipantId());
    }
}
