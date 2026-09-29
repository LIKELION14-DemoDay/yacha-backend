package likelion.yacha_backend.domain.session.game;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import lombok.Getter;

/**
 * 진행 중인 게임 하나의 메모리 상태 (명세 1-5). 매칭이 성사될 때 만들고 게임이 끝나면 버립니다.
 *
 * <p><b>잠금</b> — 공개 메서드는 모두 이 객체로 {@code synchronized} 합니다. {@code seqNo} 채번과
 * 최종변론 1건 제한이 같은 게임 안에서 한 번에 한 요청씩 처리됩니다. 게임끼리는 서로 막지 않습니다.
 * 기록한 메시지를 브로드캐스트할 때도 순서가 {@code seqNo} 와 같아야 하므로, 호출하는 쪽은
 * {@code synchronized (game) { append → 전송 }} 처럼 <b>같은 락 안에서 전송</b>합니다.
 *
 * <p><b>종료</b> — 게임이 끝났는지는 DB 의 {@code status} 가 기준입니다. {@code finishIfInProgress} 가
 * 성공한 쪽이 {@link #finish()} 를 불러 새 메시지를 바로 막습니다. {@code finish()} 는 메시지를 지우지 않습니다.
 * 정상 종료는 판정 LLM 이 대화 전체({@code messagesAfter(0)})를 읽어야 하므로, 채팅은 <b>판정이 끝난 뒤</b>
 * 버립니다. {@code FORFEIT} · {@code ABORTED} 는 판정이 없으니 종료 즉시 저장소에서 지웁니다 (명세 1-5).
 *
 * <p><b>채팅 본문을 로그 · 예외 메시지에 넣지 마세요</b> (명세 1-5).
 */
public class Game {

    /** 최종변론 글자 수 상한. 명세 2-4 에서 확정된 값입니다. */
    public static final int FINAL_MAX_LENGTH = 100;

    @Getter
    private final Long sessionId;

    /** 구간 계산 기준. {@code DebateSession.startedAt} 과 같은 값입니다. */
    @Getter
    private final LocalDateTime startedAt;

    /**
     * 사람 참가자의 userId → participantId. 채팅마다 DB 를 조회하지 않고 여기서 참가자인지 확인합니다.
     * AI 참가자는 userId 가 없어 들어 있지 않습니다.
     */
    private final Map<Long, Long> participantIdByUserId;

    private final int chatMaxLength;
    private final int maxMessages;

    /** seqNo 오름차순. seqNo 는 1 부터 빈틈없이 매기므로 인덱스 = seqNo - 1 입니다. */
    private final List<GameMessage> messages = new ArrayList<>();
    private final Set<Long> finalSubmitted = new HashSet<>();
    private boolean finished;

    Game(Long sessionId, LocalDateTime startedAt, Map<Long, Long> participantIdByUserId,
         int chatMaxLength, int maxMessages) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.participantIdByUserId = Map.copyOf(participantIdByUserId);
        this.chatMaxLength = chatMaxLength;
        this.maxMessages = maxMessages;
    }

    /**
     * 채팅을 기록합니다. {@code CHAT_1} · {@code CHAT_2} 구간에서만 받습니다.
     *
     * @param now 서버가 받은 시각. 구간은 이 시각으로 판단합니다
     */
    public synchronized GameMessage appendChat(Long userId, String content, LocalDateTime now) {
        Long participantId = checkWritable(userId);
        DebatePhase phase = DebatePhase.at(startedAt, now).phase();
        if (!phase.isChatAllowed()) {
            throw new BusinessException(SessionErrorCode.INVALID_PHASE);
        }
        checkLength(content, chatMaxLength);
        return append(participantId, MessageType.CHAT, phase, content, now);
    }

    /**
     * 최종변론을 기록합니다. {@code FINAL} 구간에서 참가자당 1건, 100자 이내입니다.
     *
     * @param now 서버가 받은 시각. 구간은 이 시각으로 판단합니다
     */
    public synchronized GameMessage submitFinal(Long userId, String content, LocalDateTime now) {
        Long participantId = checkWritable(userId);
        DebatePhase phase = DebatePhase.at(startedAt, now).phase();
        if (!phase.isFinalAllowed()) {
            throw new BusinessException(SessionErrorCode.INVALID_PHASE);
        }
        if (finalSubmitted.contains(participantId)) {
            throw new BusinessException(SessionErrorCode.FINAL_ALREADY_SUBMITTED);
        }
        checkLength(content, FINAL_MAX_LENGTH);
        GameMessage message = append(participantId, MessageType.FINAL, phase, content, now);
        finalSubmitted.add(participantId);
        return message;
    }

    /** {@code seqNo > afterSeq} 인 메시지를 오름차순으로. 재접속 보충 · 늦게 들어온 관전자용입니다. */
    public synchronized List<GameMessage> messagesAfter(long afterSeq) {
        int from = (int) Math.min(Math.max(afterSeq, 0), messages.size());
        return List.copyOf(messages.subList(from, messages.size()));
    }

    /** 이 사용자의 참가자 id. 참가자가 아니면(관전자 포함) null. */
    public Long participantIdOf(Long userId) {
        return participantIdByUserId.get(userId);
    }

    public boolean isParticipant(Long userId) {
        return participantIdByUserId.containsKey(userId);
    }

    /** 이후 채팅 · 최종변론을 받지 않습니다. 이미 받은 메시지는 결과 화면 보관 동안 조회할 수 있습니다. */
    public synchronized void finish() {
        finished = true;
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    /** 글자 수. 이모지처럼 두 칸(char)을 쓰는 문자도 한 글자로 셉니다. */
    static int lengthOf(String content) {
        return content.codePointCount(0, content.length());
    }

    /** 끝난 게임인지 → 참가자인지 순서로 확인합니다. 관전자가 보내면 구간과 상관없이 NOT_PARTICIPANT 입니다. */
    private Long checkWritable(Long userId) {
        if (finished) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        Long participantId = participantIdByUserId.get(userId);
        if (participantId == null) {
            throw new BusinessException(SessionErrorCode.NOT_PARTICIPANT);
        }
        return participantId;
    }

    /** 비어 있거나 공백뿐인 메시지는 받지 않습니다. 요청 본문에 {@code content} 가 빠지면 null 로 들어옵니다. */
    private void checkLength(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(GlobalErrorCode.VALIDATION_FAILED);
        }
        if (lengthOf(content) > maxLength) {
            throw new BusinessException(SessionErrorCode.CONTENT_TOO_LONG);
        }
    }

    private GameMessage append(Long participantId, MessageType type, DebatePhase phase,
                               String content, LocalDateTime now) {
        if (messages.size() >= maxMessages) {
            throw new BusinessException(SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
        }
        GameMessage message = new GameMessage(messages.size() + 1L, participantId, type, phase, content, now);
        messages.add(message);
        return message;
    }
}
