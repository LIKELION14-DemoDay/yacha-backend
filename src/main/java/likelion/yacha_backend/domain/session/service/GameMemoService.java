package likelion.yacha_backend.domain.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import likelion.yacha_backend.domain.session.dto.MemoResponse;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@code PREP} · {@code REBUTTAL} 주장 작성 (명세 2-4).
 *
 * <p>작성 중인 주장은 <b>본인만</b> 봅니다. 그래서 브로드캐스트하지 않고 REST 로 저장 · 조회합니다.
 * 상대 · 관전자는 다음 채팅 구간이 시작될 때 공개되는 {@code ARGUMENT} 메시지로만 봅니다.
 *
 * <p>채팅처럼 DB 를 거치지 않고 게임 메모리에만 둡니다. <b>주장 본문을 로그에 남기지 않습니다.</b>
 */
@Service
@RequiredArgsConstructor
public class GameMemoService {

    private final GameRegistry gameRegistry;
    private final Clock clock;

    /** 현재 작성 구간의 내 주장을 덮어씁니다. */
    public MemoResponse saveMemo(Long sessionId, Long userId, String content) {
        return MemoResponse.from(findGame(sessionId).saveMemo(userId, content, LocalDateTime.now(clock)),
                clock.getZone());
    }

    /** 내가 작성한 주장 (작성 구간 순). 새로고침했을 때 작성하던 내용을 되살리는 용도입니다. */
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
