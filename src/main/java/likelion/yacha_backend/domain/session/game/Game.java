package likelion.yacha_backend.domain.session.game;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
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
 * <p><b>판정</b> — 정상 종료 뒤 판정 상태({@link JudgeStatus})와 결과({@link Verdict})도 여기에 둡니다.
 * 판정이 끝나면(성공 · 실패) 결과 화면 보관 시간 뒤에 게임째 저장소에서 지웁니다 ({@code JudgeService}).
 *
 * <p><b>주장 · 반론</b> — {@code PREP} 에는 주장, {@code REBUTTAL} 에는 반론을 제출합니다({@link #saveMemo}).
 * 공개 전에는 본인만 보고, 작성 구간이 끝나고 {@link DebatePhase#SUBMIT_GRACE} 가 지나면 {@link #revealArguments} 가
 * 양쪽 글을 {@link MessageType#ARGUMENT} 메시지로 기록합니다 (주장 63초, 반론 143초). 채팅은 반론이 공개된 뒤에만
 * 받고, 기록하기 전에 공개부터 하므로 스케줄러보다 채팅이 먼저 와도 반론이 앞 {@code seqNo} 를 받습니다.
 *
 * <p><b>시간표</b> — 구간은 {@code startedAt} 이 아니라 바뀔 수 있는 기준 시각으로 계산합니다 ({@link #timeline()}).
 * 작성 구간에 양쪽이 모두 진짜 글을 내면 남은 시간만큼 기준을 앞당겨 바로 다음 구간으로 넘기고, 그 구간 글은
 * 유예 없이 공개합니다 ({@link #advanceIfAllSubmitted}). 봇의 임시 글("생각중") · 실패 글은 제출로 치지 않습니다.
 *
 * <p><b>봇</b> — 봇전은 봇 몫의 주장 · 반론 · 채팅을 AI 참가자 id 로 기록합니다({@link #saveBotMemo} ·
 * {@link #appendBotChat}). 규칙(구간 · 글자 수 · 채팅 수)은 사람과 같고, 공개 · 순서 · 판정 입력도 같습니다.
 *
 * <p><b>힌트</b> — 사람 참가자마다 매칭 직후 만들기 시작한 힌트({@link GameHints})를 둡니다. 본인만 조회하고,
 * 게임이 끝난 뒤 늦게 도착한 결과는 버립니다.
 *
 * <p><b>연결</b> — 사람 참가자마다 지금 인정하는 연결(토론방을 마지막으로 구독한 STOMP 세션)을 둡니다.
 * 새로고침처럼 새 연결이 먼저 붙고 예전 연결이 늦게 끊기면 그 끊김은 무시합니다. 인정하는 연결이 끊기면
 * 이탈 유예가 시작되고({@link #disconnect}), 유예 안에 다시 구독하면 복귀합니다({@link #connect}).
 *
 * <p><b>채팅 · 주장 본문을 로그 · 예외 메시지에 넣지 마세요</b> (명세 1-5).
 */
public class Game {

    @Getter
    private final Long sessionId;

    /** 게임이 시작된 시각. {@code DebateSession.startedAt} 과 같은 값이고 시간표가 앞당겨져도 바뀌지 않습니다. */
    @Getter
    private final LocalDateTime startedAt;

    /** 구간 계산 기준 시각. 처음엔 {@link #startedAt} 이고, 작성 구간이 일찍 닫히면 남은 시간만큼 앞당깁니다. */
    private LocalDateTime timelineBase;
    /** 시간표 버전. 기준 시각을 앞당길 때마다 올립니다. */
    private long timelineVersion;
    /** 양쪽이 다 내서 일찍 닫힌 작성 구간. */
    private final Set<DebatePhase> closedEarlyPhases = EnumSet.noneOf(DebatePhase.class);

    /**
     * 사람 참가자의 userId → participantId. 채팅마다 DB 를 조회하지 않고 여기서 참가자인지 확인합니다.
     * AI 참가자는 userId 가 없어 들어 있지 않습니다.
     */
    private final Map<Long, Long> participantIdByUserId;

    /** 봇전의 AI 참가자 id. 사람전이면 null. 봇의 주장 · 반론 · 채팅을 기록할 때 씁니다. */
    @Getter
    private final Long aiParticipantId;

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
    /** 봇이 지금 임시 글(생각중 · 실패 글)을 내 둔 작성 구간. 진짜 글로 덮어쓰면 빠집니다. */
    private final Set<DebatePhase> provisionalBotPhases = EnumSet.noneOf(DebatePhase.class);
    private boolean finished;
    /** 판정 상태. 판정을 시작하기 전에는 null 입니다. */
    private JudgeStatus judgeStatus;
    /** 판정이 끝나면({@code READY}) 들어갑니다. */
    private Verdict verdict;
    /** 판정을 시작한 횟수. 처음 판정 + 재판정입니다. */
    private int judgeRuns;
    /** 봇전의 봇 글 진행 상태. 사람전이면 null 입니다. */
    private final BotProgress botProgress;
    /** 사람 참가자의 userId → 힌트. 만들기 시작하기 전이면 들어 있지 않습니다. */
    private final Map<Long, GameHints> hintsByUserId = new HashMap<>();
    /** 사람 참가자의 userId → 지금 인정하는 연결(STOMP 세션 id). 토론방을 마지막으로 구독한 연결입니다. */
    private final Map<Long, String> connectionByUserId = new HashMap<>();
    /** 연결이 끊겨 이탈 유예 중인 userId → 몇 번째 끊김인지. 유예 타이머가 자기 끊김인지 확인합니다. */
    private final Map<Long, Long> disconnectNoByUserId = new HashMap<>();
    private long disconnectCount;

    Game(Long sessionId, LocalDateTime startedAt, Map<Long, Long> participantIdByUserId, Long aiParticipantId,
         int chatMaxLength, int maxChatsPerParticipant, int argumentMaxLength, int rebuttalMaxLength) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.timelineBase = startedAt;
        this.participantIdByUserId = Map.copyOf(participantIdByUserId);
        this.aiParticipantId = aiParticipantId;
        this.botProgress = aiParticipantId == null ? null : new BotProgress();
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
        return appendChatOf(checkWritable(userId), content, now);
    }

    /**
     * 봇전의 봇 채팅을 기록합니다. 규칙은 사람 채팅과 같습니다 (구간 · 글자 수 · 참가자별 채팅 수).
     *
     * @throws IllegalStateException 사람전 (AI 참가자가 없음)
     */
    public synchronized GameMessage appendBotChat(String content, LocalDateTime now) {
        return appendChatOf(checkBotWritable(), content, now);
    }

    private GameMessage appendChatOf(Long participantId, String content, LocalDateTime now) {
        revealArguments(now);
        DebatePhase phase = DebatePhase.at(timelineBase, now).phase();
        // 반론이 공개된 뒤에만 받습니다. 바로 위에서 공개할 때가 된 글은 공개했으므로 공개 여부로 봅니다.
        if (!phase.isChatAllowed() || !revealedPhases.contains(DebatePhase.REBUTTAL)) {
            throw new BusinessException(SessionErrorCode.INVALID_PHASE);
        }
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
        return saveMemoOf(checkWritable(userId), content, now);
    }

    /**
     * 봇전의 봇 주장 · 반론을 제출합니다. 규칙은 사람 제출과 같습니다 (작성 구간 · 유예 3초 · 공개 전 · 글자 수).
     *
     * @throws IllegalStateException 사람전 (AI 참가자가 없음)
     */
    public synchronized GameMemo saveBotMemo(String content, LocalDateTime now) {
        return saveBotMemo(content, false, now);
    }

    /**
     * 봇 글을 제출합니다. {@code provisional} 이면 임시 글(생각중 · 실패 글)이라 구간을 일찍 넘기는 제출로 치지 않습니다.
     *
     * @throws IllegalStateException 사람전 (AI 참가자가 없음)
     */
    public synchronized GameMemo saveBotMemo(String content, boolean provisional, LocalDateTime now) {
        GameMemo memo = saveMemoOf(checkBotWritable(), content, now);
        if (provisional) {
            provisionalBotPhases.add(memo.phase());
        } else {
            provisionalBotPhases.remove(memo.phase());
        }
        return memo;
    }

    /** 봇전의 봇 글 진행 상태. 사람전이면 null 입니다. */
    public BotProgress botProgress() {
        return botProgress;
    }

    /** 봇이 보낸 채팅 수. 사람전이면 0 입니다. */
    public synchronized int botChatCount() {
        return aiParticipantId == null ? 0 : chatCountByParticipantId.getOrDefault(aiParticipantId, 0);
    }

    /** 사람 참가자가 채팅을 한 번이라도 보냈는가. 봇의 첫마디를 정할 때 씁니다. */
    public synchronized boolean hasHumanChat() {
        return chatCountByParticipantId.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(aiParticipantId) && entry.getValue() > 0);
    }

    private GameMemo saveMemoOf(Long participantId, String content, LocalDateTime now) {
        DebatePhase phase = memoPhaseAt(now);
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
        DebatePhase phase = memoPhaseAt(now);
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
            if (!memoPhase.isMemoAllowed() || now.isBefore(timeline().revealAt(memoPhase))
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

    /**
     * 지금 작성 구간에 양쪽 모두 진짜 글을 냈으면 남은 시간을 건너뛰고 다음 구간으로 넘깁니다. 넘겼으면 그 결과를,
     * 아니면 null 을 돌려줍니다. 주장 · 반론을 제출한 직후 같은 락 안에서 부릅니다.
     *
     * <p>기준 시각을 남은 시간만큼 앞당기고 버전을 올립니다. 닫힌 구간은 유예 없이 지금 공개하므로 그 뒤로는 고칠 수
     * 없습니다. 작성 구간이 이미 끝나 유예 중이면(3초) 넘기지 않습니다 — 다음 구간 알림이 이미 나갔습니다.
     *
     * @param phase 방금 제출한 글의 작성 구간
     */
    public synchronized PhaseAdvance advanceIfAllSubmitted(DebatePhase phase, LocalDateTime now) {
        if (finished || !phase.isMemoAllowed() || DebatePhase.at(timelineBase, now).phase() != phase
                || !allSubmitted(phase)) {
            return null;
        }
        timelineBase = timelineBase.minus(Duration.between(now, phase.endAt(timelineBase)));
        timelineVersion++;
        closedEarlyPhases.add(phase);
        List<GameMessage> revealed = revealArguments(now);
        DebatePhase next = DebatePhase.at(timelineBase, now).phase();
        return new PhaseAdvance(next, next.endAt(timelineBase), now, revealed);
    }

    /** 지금 시간표. 기준 시각 · 버전 · 일찍 닫힌 구간을 한 번에 찍어 줍니다. */
    public synchronized GameTimeline timeline() {
        return new GameTimeline(timelineBase, timelineVersion, closedEarlyPhases);
    }

    /** {@code now} 에 제출을 받는 작성 구간. 작성 구간(유예 포함)이 아니거나 일찍 닫혔으면 null 입니다. */
    public synchronized DebatePhase memoPhase(LocalDateTime now) {
        return memoPhaseAt(now);
    }

    /** 이 작성 구간의 글을 이미 공개했는가. */
    public synchronized boolean isRevealed(DebatePhase phase) {
        return revealedPhases.contains(phase);
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

    /** 사람 참가자의 userId. 봇은 들어 있지 않습니다. */
    public Set<Long> humanUserIds() {
        return participantIdByUserId.keySet();
    }

    /** 이 참가자의 힌트를 만들기 시작합니다 ({@code PENDING}). */
    public synchronized void startHints(Long userId) {
        if (!isParticipant(userId)) {
            throw new IllegalArgumentException("사람 참가자가 아닙니다. sessionId=" + sessionId);
        }
        hintsByUserId.put(userId, GameHints.PENDING);
    }

    /** 만든 힌트를 저장합니다. 게임이 이미 끝났으면 버립니다. */
    public synchronized void completeHints(Long userId, List<String> hints) {
        if (!finished) {
            hintsByUserId.put(userId, GameHints.ready(hints));
        }
    }

    /** 힌트를 만들지 못했습니다. 다시 만들지 않습니다. 게임이 이미 끝났으면 아무것도 하지 않습니다. */
    public synchronized void failHints(Long userId) {
        if (!finished) {
            hintsByUserId.put(userId, GameHints.FAILED);
        }
    }

    /** 이 참가자의 힌트. 아직 만들기 시작하기 전(매칭 직후 아주 짧은 사이)이면 곧 시작하므로 {@code PENDING} 입니다. */
    public synchronized GameHints hintsOf(Long userId) {
        return hintsByUserId.getOrDefault(userId, GameHints.PENDING);
    }

    /**
     * 토론방을 구독한 연결을 이 사용자의 연결로 인정합니다. 이탈 유예 중이었으면 복귀로 보고 true 를 돌려줍니다.
     * 참가자가 아니거나(관전자) 끝난 게임이면 아무것도 하지 않고 false 입니다.
     */
    public synchronized boolean connect(Long userId, String connectionId) {
        if (finished || !isParticipant(userId)) {
            return false;
        }
        connectionByUserId.put(userId, Objects.requireNonNull(connectionId, "connectionId"));
        return disconnectNoByUserId.remove(userId) != null;
    }

    /**
     * 연결이 끊겼습니다. 이 사용자가 지금 인정하는 연결이면 이탈 유예를 시작하고 몇 번째 끊김인지 돌려줍니다.
     * 예전 연결이거나, 이미 유예 중이거나, 끝난 게임이면 비어 있습니다.
     */
    public synchronized OptionalLong disconnect(Long userId, String connectionId) {
        if (finished || !connectionId.equals(connectionByUserId.get(userId)) || disconnectNoByUserId.containsKey(userId)) {
            return OptionalLong.empty();
        }
        long disconnectNo = ++disconnectCount;
        disconnectNoByUserId.put(userId, disconnectNo);
        return OptionalLong.of(disconnectNo);
    }

    /** {@code disconnectNo} 번째 끊김 뒤로 이 사용자가 아직 돌아오지 않았는가. 유예 타이머가 확인합니다. */
    public synchronized boolean isStillDisconnected(Long userId, long disconnectNo) {
        Long current = disconnectNoByUserId.get(userId);
        return current != null && current == disconnectNo;
    }

    /** 이 사용자 말고 다른 사람 참가자도 이탈 유예 중인가 (양쪽 동시 이탈). */
    public synchronized boolean isOtherDisconnected(Long userId) {
        return disconnectNoByUserId.keySet().stream().anyMatch(other -> !other.equals(userId));
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

    /**
     * 판정을 시작합니다. 처음이거나 실패({@code FAILED})한 뒤에만 {@code PENDING} 으로 바꾸고 true 를 돌려줍니다.
     * 이미 판정 중이거나 끝났으면 false 라, 260초 종료와 결과 재요청이 겹쳐도 판정은 한 번만 돕니다.
     *
     * <p>시작한 횟수가 {@code maxRuns} 에 이르면 실패한 뒤에도 false 입니다. 결과 화면 폴링마다 재판정이 돌아
     * LLM 호출이 끝없이 늘지 않게 합니다.
     *
     * @param maxRuns 판정을 시작할 수 있는 최대 횟수 (처음 판정 + 재판정)
     * @throws IllegalStateException 아직 끝나지 않은 게임. 판정은 대화가 더 늘지 않을 때만 합니다
     */
    public synchronized boolean startJudging(int maxRuns) {
        if (!finished) {
            throw new IllegalStateException("끝나지 않은 게임은 판정할 수 없습니다. sessionId=" + sessionId);
        }
        if (judgeStatus == JudgeStatus.PENDING || judgeStatus == JudgeStatus.READY || judgeRuns >= maxRuns) {
            return false;
        }
        judgeStatus = JudgeStatus.PENDING;
        judgeRuns++;
        return true;
    }

    public synchronized void completeJudging(Verdict verdict) {
        this.verdict = Objects.requireNonNull(verdict, "verdict");
        this.judgeStatus = JudgeStatus.READY;
    }

    public synchronized void failJudging() {
        this.judgeStatus = JudgeStatus.FAILED;
    }

    /** 판정 상태. 판정을 시작하기 전이면 null 입니다. */
    public synchronized JudgeStatus judgeStatus() {
        return judgeStatus;
    }

    /** 판정 결과. {@code READY} 가 아니면 null 입니다. */
    public synchronized Verdict verdict() {
        return verdict;
    }

    private DebatePhase memoPhaseAt(LocalDateTime now) {
        DebatePhase phase = DebatePhase.memoPhaseAt(timelineBase, now);
        return phase == null || closedEarlyPhases.contains(phase) ? null : phase;
    }

    /** 참가자 모두(사람 · 봇) 이 작성 구간에 글을 냈는가. 봇의 임시 글은 치지 않습니다. */
    private boolean allSubmitted(DebatePhase phase) {
        List<Long> participantIds = new ArrayList<>(participantIdByUserId.values());
        if (aiParticipantId != null) {
            if (provisionalBotPhases.contains(phase)) {
                return false;
            }
            participantIds.add(aiParticipantId);
        }
        return participantIds.stream()
                .allMatch(id -> memosByParticipantId.getOrDefault(id, Map.of()).containsKey(phase));
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

    /** 봇 글을 쓸 수 있는지 확인합니다. 끝난 게임이면 사람과 같이 SESSION_NOT_IN_PROGRESS 입니다. */
    private Long checkBotWritable() {
        if (aiParticipantId == null) {
            throw new IllegalStateException("봇전이 아닙니다. sessionId=" + sessionId);
        }
        if (finished) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        return aiParticipantId;
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
