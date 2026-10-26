package likelion.yacha_backend.domain.session.game;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 진행 중인 게임의 메모리 저장소. 세션 id → {@link Game} (명세 1-5).
 *
 * <p>채팅 내용은 DB · Redis 어디에도 두지 않으므로 이 맵이 유일한 저장소입니다.
 * <b>서버 1대 전제</b>이고(simple broker 와 같은 전제), 서버가 재시작되면 전부 사라집니다.
 * 그래서 기동 시 {@code IN_PROGRESS} 세션은 이어가지 않고 {@code ABORTED} 로 정리합니다.
 *
 * <p>게임을 만드는 곳은 매칭 성사 시점(DB 를 {@code IN_PROGRESS} 로 바꾼 직후)이고,
 * 지우는 곳은 게임 종료 처리입니다. 몰수패 · 무효는 종료 즉시, 정상 종료는 결과 화면 보관 시간이 지난 뒤입니다.
 */
@Component
@EnableConfigurationProperties(GameProperties.class)
public class GameRegistry {

    private final Map<Long, Game> games = new ConcurrentHashMap<>();
    private final GameProperties properties;

    public GameRegistry(GameProperties properties) {
        this.properties = properties;
    }

    /** 사람전 게임을 만듭니다. */
    public Game create(Long sessionId, LocalDateTime startedAt, Map<Long, Long> participantIdByUserId) {
        return create(sessionId, startedAt, participantIdByUserId, null);
    }

    /**
     * 게임을 만듭니다. 같은 세션의 게임이 이미 있으면 예외입니다 (매칭이 두 번 성사된 버그).
     *
     * @param participantIdByUserId 사람 참가자의 userId → participantId. 봇전이면 사용자 한 명만 들어 있습니다
     * @param aiParticipantId       봇전의 AI 참가자 id. 사람전이면 null
     */
    public Game create(Long sessionId, LocalDateTime startedAt, Map<Long, Long> participantIdByUserId,
                       Long aiParticipantId) {
        Game game = new Game(sessionId, startedAt, participantIdByUserId, aiParticipantId,
                properties.chatMaxLength(), properties.maxChatsPerParticipant(),
                properties.argumentMaxLength(), properties.rebuttalMaxLength());
        if (games.putIfAbsent(sessionId, game) != null) {
            throw new IllegalStateException("이미 게임이 있는 세션입니다. sessionId=" + sessionId);
        }
        return game;
    }

    /** 게임이 없으면(시작 전 · 이미 정리됨 · 서버 재시작) 비어 있습니다. */
    public Optional<Game> find(Long sessionId) {
        return Optional.ofNullable(games.get(sessionId));
    }

    /**
     * 게임을 지웁니다. 맵에서 빼기 전에 {@link Game#finish()} 로 쓰기부터 막습니다.
     *
     * <p>맵에서만 빼면 직전에 {@link #find} 로 게임을 받아 둔 요청이 지워진 게임에 채팅을 기록 · 전송할 수 있고,
     * 그 메시지는 {@code /messages} 로도 복구되지 않습니다. {@code finish()} 는 게임 락을 잡으므로 진행 중인
     * 기록 · 전송이 끝난 뒤에 적용되고, 그 뒤의 기록은 {@code SESSION_NOT_IN_PROGRESS} 로 거부됩니다.
     */
    public void remove(Long sessionId) {
        Game game = games.get(sessionId);
        if (game == null) {
            return;
        }
        game.finish();
        games.remove(sessionId, game);
    }
}
