package likelion.yacha_backend.domain.session.game;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import likelion.yacha_backend.domain.session.entity.DebatePhase;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import lombok.Getter;

/**
 * 진행 중인 게임 하나의 메모리 상태 (명세 1-5). 매칭이 성사될 때 만들고 게임이 끝나면 버립니다.
 *
 * <p><b>잠금</b> — 공개 메서드는 모두 이 객체로 {@code synchronized} 합니다. {@code seqNo} 채번 · 공개 순서 ·
 * 참가자별 채팅 수가 같은 게임 안에서 한 번에 한 요청씩 처리됩니다. 게임끼리는 서로 막지 않습니다.
 * 기록한 메시지를 브로드캐스트할 때도 순서가 {@code seqNo} 와 같아야 하므로, 호출하는 쪽은
 * {@code synchronized (game) { append → 전송 }} 처럼 <b>같은 락 안에서 전송</b>합니다.
 *
 * <p><b>종료</b> — 게임이 끝났는지는 DB 의 {@code status} 가 기준입니다. {@code finishIfInProgress} 가
 * 성공한 쪽이 {@link #finish()} 를 불러 새 메시지를 바로 막습니다. {@code finish()} 는 메시지를 지우지 않습니다.
 * 정상 종료는 판정 LLM 이 대화 전체({@code messagesAfter(0)})를 읽어야 하므로, 채팅은 <b>판정이 끝난 뒤</b>
 * 버립니다. {@code FORFEIT} · {@code ABORTED} 는 판정이 없으니 종료 즉시 저장소에서 지웁니다 (명세 1-5).
 *
 * <p><b>주장 · 반론</b> — {@code PREP} 에는 주장, {@code REBUTTAL} 에는 반론을 제출합니다({@link #saveMemo}).
 * 공개 전에는 본인만 보고, 작성 구간이 끝나고 {@link DebatePhase#SUBMIT_GRACE} 가 지나면 {@link #revealArguments} 가
 * 양쪽 글을 {@link MessageType#ARGUMENT} 메시지로 기록합니다 (주장 63초, 반론 143초). 채팅은 반론이 공개된 뒤에만
 * 받고, 기록하기 전에 공개부터 하므로 스케줄러보다 채팅이 먼저 와도 반론이 앞 {@code seqNo} 를 받습니다.
 *
 * <p><b>채팅 · 주장 본문을 로그 · 예외 메시지에 넣지 마세요</b> (명세 1-5).
 */
public class Game {

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

    /**
     * 참가자 한 명이 보낼 수 있는 채팅 수. 게임 전체로 세면 한쪽이 상한을 채웠을 때 상대가 채팅을 못 보내므로
     * 참가자별로 셉니다. 공개된 주장 · 반론은 판정에 꼭 필요해 세지 않습니다.
     */
    private final int maxChatsPerParticipant;

    /** {@code PREP} 주장의 글자 수 상한. */
    private final int argumentMaxLength;

    /** {@code REBUTTAL} 반론의 글자 수 상한. */
    private final int rebuttalMaxLength;

    /** seqNo 오름차순. seqNo 는 1 부터 빈틈없이 매기므로 인덱스 = seqNo - 1 입니다. */
    private final List<GameMessage> messages = new ArrayList<>();
    private final Map<Long, Integer> chatCountByParticipantId = new HashMap<>();
    /** 참가자 id → 작성 구간 → 제출한 글. 공개한 뒤에도 본인 조회용으로 남겨 둡니다. */
    private final Map<Long, Map<DebatePhase, GameMemo>> memosByParticipantId = new HashMap<>();
    /** 이미 공개한 작성 구간. 구간마다 한 번만 공개합니다. */
    private final Set<DebatePhase> revealedPhases = EnumSet.noneOf(DebatePhase.class);
    private boolean finished;

    Game(Long sessionId, LocalDateTime startedAt, Map<Long, Long> participantIdByUserId,
         int chatMaxLength, int maxChatsPerParticipant, int argumentMaxLength, int rebuttalMaxLength) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.participantIdByUserId = Map.copyOf(participantIdByUserId);
        this.chatMaxLength = chatMaxLength;
        this.maxChatsPerParticipant = maxChatsPerParticipant;
        this.argumentMaxLength = argumentMaxLength;
        this.rebuttalMaxLength = rebuttalMaxLength;
    }

    /**
     * 채팅을 기록합니다. {@code CHAT} 구간에서 반론이 공개된 뒤(143초)부터 받습니다.
     *
     * <p>기록하기 전에 아직 공개하지 않은 글을 먼저 공개합니다. 공개된 글은 반환값에 없으므로,
     * 브로드캐스트하는 쪽은 같은 락 안에서 {@link #revealArguments} 를 먼저 불러 받아 갑니다.
     *
     * @param now 서버가 받은 시각. 구간은 이 시각으로 판단합니다
     */
    public synchronized GameMessage appendChat(Long userId, String content, LocalDateTime now) {
        Long participantId = checkWritable(userId);
        revealArguments(now);
        if (!DebatePhase.isChatOpen(startedAt, now)) {
            throw new BusinessException(SessionErrorCode.INVALID_PHASE);
        }
        DebatePhase phase = DebatePhase.at(startedAt, now).phase();
        checkLength(content, chatMaxLength);
        int chatCount = chatCountByParticipantId.getOrDefault(participantId, 0);
        if (chatCount >= maxChatsPerParticipant) {
            throw new BusinessException(SessionErrorCode.MESSAGE_LIMIT_EXCEEDED);
        }
        GameMessage message = append(participantId, MessageType.CHAT, phase, content, now);
        chatCountByParticipantId.put(participantId, chatCount + 1);
        return message;
    }

    /**
     * 주장(PREP) · 반론(REBUTTAL)을 제출합니다. 참가자마다 구간당 하나이고, 다시 제출하면 덮어씁니다.
     * 작성 구간이 끝난 뒤에도 {@link DebatePhase#SUBMIT_GRACE} 동안은 받습니다 — 시간 종료 순간의 자동 제출이
     * 늦게 도착하기 때문입니다. 빈 글도 제출로 받고, 빈 글은 공개하지 않습니다.
     *
     * <p>이미 공개된 구간의 글은 고칠 수 없습니다. 락을 기다리는 사이 공개됐는데 그보다 이른 {@code now} 로
     * 저장하면, 공개된 {@code ARGUMENT} 와 본인이 보는 글이 달라지기 때문입니다.
     *
     * @param now 서버가 받은 시각. 구간은 이 시각으로 판단합니다
     */
    public synchronized GameMemo saveMemo(Long userId, String content, LocalDateTime now) {
        Long participantId = checkWritable(userId);
        DebatePhase phase = DebatePhase.memoPhaseAt(startedAt, now);
        if (phase == null || revealedPhases.contains(phase)) {
            throw new BusinessException(SessionErrorCode.INVALID_PHASE);
        }
        if (content == null) {
            throw new BusinessException(GlobalErrorCode.VALIDATION_FAILED);
        }
        int maxLength = phase == DebatePhase.PREP ? argumentMaxLength : rebuttalMaxLength;
        if (lengthOf(content) > maxLength) {
            throw new BusinessException(SessionErrorCode.CONTENT_TOO_LONG);
        }
        GameMemo memo = new GameMemo(phase, content, now);
        memosByParticipantId.computeIfAbsent(participantId, id -> new EnumMap<>(DebatePhase.class)).put(phase, memo);
        return memo;
    }

    /** 내가 제출한 글을 작성 구간 순서대로. 참가자가 아니면 NOT_PARTICIPANT 이고, 남의 글을 보는 방법은 없습니다. */
    public synchronized List<GameMemo> memosOf(Long userId) {
        Long participantId = checkWritable(userId);
        return List.copyOf(memosByParticipantId.getOrDefault(participantId, Map.of()).values());
    }

    /**
     * {@code now} 에 제출을 받는 작성 구간에서 이미 제출한 참가자 id. 작성 구간(유예 포함)이 아니면 null 입니다.
     * 내용은 주지 않습니다 — "상대방이 아직 작성중입니다" 화면용입니다.
     */
    public synchronized Set<Long> submittedParticipantIds(LocalDateTime now) {
        DebatePhase phase = DebatePhase.memoPhaseAt(startedAt, now);
        if (phase == null) {
            return null;
        }
        return memosByParticipantId.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(phase))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 공개할 때가 된 글을 {@link MessageType#ARGUMENT} 메시지로 기록하고, 이번에 기록한 메시지를 {@code seqNo} 순으로 돌려줍니다.
     *
     * <p>작성 구간이 끝나고 {@link DebatePhase#SUBMIT_GRACE} 가 지나면 그 구간의 글을 공개합니다 — {@code PREP} 주장은
     * 63초, {@code REBUTTAL} 반론은 143초. 구간마다 한 번만 공개하고, 늦게 불려도 공개하지 못한 앞 구간 글부터 차례로
     * 공개합니다. 메시지의 구간({@code phase})은 작성한 구간입니다. 빈 글은 공개하지 않습니다.
     *
     * <p>구간 스케줄러가 공개 시각에 부르고, 채팅 · 제출을 처리할 때도 먼저 부릅니다.
     * 끝난 게임이면 아무것도 하지 않습니다. 참가자별 채팅 수 상한에는 세지 않습니다.
     *
     * <p><b>구간 스케줄러 전까지(#49)</b>는 채팅 · 제출을 처리할 때만 공개합니다. 공개 시각이 지나도 아무도 보내지
     * 않으면 공개되지 않아 {@code /messages} 와 판정 입력에서도 빠집니다.
     */
    public synchronized List<GameMessage> revealArguments(LocalDateTime now) {
        if (finished) {
            return List.of();
        }
        List<GameMessage> revealed = new ArrayList<>();
        for (DebatePhase memoPhase : DebatePhase.values()) {
            if (!memoPhase.isMemoAllowed() || now.isBefore(memoPhase.revealAt(startedAt))
                    || !revealedPhases.add(memoPhase)) {
                continue;
            }
            memosByParticipantId.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        GameMemo memo = entry.getValue().get(memoPhase);
                        if (memo != null && !memo.content().isBlank()) {
                            revealed.add(append(entry.getKey(), MessageType.ARGUMENT, memoPhase, memo.content(), now));
                        }
                    });
        }
        return List.copyOf(revealed);
    }

    /** {@code seqNo > afterSeq} 인 메시지를 오름차순으로. 재접속 보충용입니다. */
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

    /**
     * 이후 채팅 · 제출을 받지 않습니다. 이미 받은 메시지는 판정 LLM 이 읽도록 남겨 둡니다.
     * 사용자의 메시지 조회는 세션 상태로 막습니다 ({@code SessionQueryService#getMessages}).
     */
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
        GameMessage message = new GameMessage(messages.size() + 1L, participantId, type, phase, content, now);
        messages.add(message);
        return message;
    }
}
