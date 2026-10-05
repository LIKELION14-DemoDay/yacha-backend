package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import likelion.yacha_backend.domain.session.dto.MemoResponse;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameMemo;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@code PREP} 주장 · {@code REBUTTAL} 반론 제출 (명세 2-4).
 *
 * <p>제출한 글은 공개 전까지 <b>본인만</b> 봅니다. 그래서 내용은 브로드캐스트하지 않고 REST 로 제출 · 조회하며,
 * 토론방에는 "제출했다"는 알림({@code ARGUMENT_SUBMITTED})만 보냅니다. 상대 · 관전자는 공개 시각에
 * {@code ARGUMENT} 메시지로 내용을 봅니다.
 *
 * <p>채팅처럼 DB 를 거치지 않고 게임 메모리에만 둡니다. <b>본문을 로그에 남기지 않습니다.</b>
 */
@Service
@RequiredArgsConstructor
public class GameMemoService {

    private final GameRegistry gameRegistry;
    private final GameMessageService gameMessageService;
    private final Clock clock;

    /**
     * 지금 작성 구간의 내 글을 제출합니다 (다시 제출하면 덮어씀).
     *
     * <p>시각은 게임 락을 잡은 뒤에 구합니다. 락 밖에서 구하면 기다리는 사이 공개됐는데도 이전 시각으로
     * 저장될 수 있습니다 ({@link GameMessageService} 와 같은 규칙). 제출하기 전에 공개할 때가 된 글을 먼저
     * 공개해 보내므로, 반론 제출이 주장 공개를 앞지르지 않습니다.
     */
    public MemoResponse saveMemo(Long sessionId, Long userId, String content) {
        Game game = findGame(sessionId);
        synchronized (game) {
            LocalDateTime now = LocalDateTime.now(clock);
            gameMessageService.broadcastAll(sessionId, game.revealArguments(now));
            GameMemo memo = game.saveMemo(userId, content, now);
            gameMessageService.broadcastSubmitted(sessionId, game.participantIdOf(userId), memo.phase());
            return MemoResponse.from(memo, clock.getZone());
        }
    }

    /** 내가 제출한 글 (작성 구간 순). 새로고침했을 때 작성하던 내용을 되살리는 용도입니다. */
    public List<MemoResponse> getMyMemos(Long sessionId, Long userId) {
        ZoneId zone = clock.getZone();
        return findGame(sessionId).memosOf(userId).stream()
                .map(memo -> MemoResponse.from(memo, zone))
                .toList();
    }

    /** 게임이 없으면 시작 전 · 종료 · 서버 재시작 중 하나입니다 ({@link GameMessageService} 와 같은 규칙). */
    private Game findGame(Long sessionId) {
        return gameRegistry.find(sessionId)
                .orElseThrow(() -> new BusinessException(SessionErrorCode.SESSION_NOT_IN_PROGRESS));
    }
}
