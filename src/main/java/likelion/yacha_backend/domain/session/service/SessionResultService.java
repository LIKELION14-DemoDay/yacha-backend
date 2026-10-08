package likelion.yacha_backend.domain.session.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import likelion.yacha_backend.domain.ai.JudgeCriterion;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.session.dto.SessionResultResponse;
import likelion.yacha_backend.domain.session.dto.SessionResultResponse.ParticipantResultResponse;
import likelion.yacha_backend.domain.session.entity.DebateParticipant;
import likelion.yacha_backend.domain.session.entity.DebateResult;
import likelion.yacha_backend.domain.session.entity.DebateSession;
import likelion.yacha_backend.domain.session.entity.FinishReason;
import likelion.yacha_backend.domain.session.entity.SessionStatus;
import likelion.yacha_backend.domain.session.exception.SessionErrorCode;
import likelion.yacha_backend.domain.session.game.Game;
import likelion.yacha_backend.domain.session.game.GameRegistry;
import likelion.yacha_backend.domain.session.game.JudgeStatus;
import likelion.yacha_backend.domain.session.game.Verdict;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결과 화면 조회 (명세 2-6). 그 세션의 참가자만 봅니다 — 관전 스위치와 상관없습니다.
 *
 * <table>
 *   <tr><th>상황</th><th>응답</th></tr>
 *   <tr><td>정상 종료, 판정 중</td><td>{@code PENDING}</td></tr>
 *   <tr><td>정상 종료, 판정 끝 (보관 시간 안)</td><td>{@code READY} + 점수 · 요약</td></tr>
 *   <tr><td>정상 종료, 판정 실패 (보관 시간 안)</td><td>판정을 다시 시작하고 {@code PENDING}</td></tr>
 *   <tr><td>DB 에 승패가 있음 (보관 시간 뒤 · 몰수패)</td><td>{@code READY} + 승패만</td></tr>
 *   <tr><td>그 밖 (재시작으로 판정 못 함 · ABORTED · 게스트끼리 보관 시간 뒤)</td><td>{@code FAILED}</td></tr>
 * </table>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionResultService {

    private final SessionAccessService sessionAccessService;
    private final DebateParticipantRepository participantRepository;
    private final GameRegistry gameRegistry;
    private final JudgeService judgeService;

    public SessionResultResponse getResult(Long sessionId, Long userId) {
        DebateSession session = sessionAccessService.getSession(sessionId);
        List<DebateParticipant> participants = participantRepository.findAllBySession_Id(sessionId);
        if (participants.stream().noneMatch(p -> p.isUser(userId))) {
            throw new BusinessException(SessionErrorCode.NOT_PARTICIPANT);
        }
        if (session.getStatus() != SessionStatus.FINISHED) {
            throw new BusinessException(SessionErrorCode.SESSION_NOT_FINISHED);
        }

        FinishReason finishReason = session.getFinishReason();
        Optional<Game> game = finishReason == FinishReason.COMPLETED ? gameRegistry.find(sessionId) : Optional.empty();
        if (game.isPresent()) {
            JudgeStatus status = game.get().judgeStatus();
            if (status == JudgeStatus.READY) {
                return judged(finishReason, game.get().verdict(), participants, userId);
            }
            if (status == JudgeStatus.FAILED) {
                judgeService.start(sessionId);
            }
            // 판정 시작 전(260초 종료 처리 직후의 아주 짧은 사이)도 곧 판정이 시작되므로 PENDING 입니다.
            return SessionResultResponse.pending();
        }
        if (participants.stream().anyMatch(p -> p.getResult() != null)) {
            return recordedOnly(finishReason, participants, userId);
        }
        return SessionResultResponse.failed(finishReason);
    }

    /** 보관 시간 안 — 메모리의 판정 결과. 게스트도 여기서는 승패가 보입니다. */
    private static SessionResultResponse judged(FinishReason finishReason, Verdict verdict,
                                                List<DebateParticipant> participants, Long userId) {
        JudgeResult judgeResult = verdict.judgeResult();
        return new SessionResultResponse(JudgeStatus.READY, finishReason, verdict.winnerParticipantId(),
                SessionResultResponse.criteria(judgeResult.summaries()),
                responses(participants, userId, p -> {
                    JudgeResult.ParticipantScore score = judgeResult.scoreOf(p.getId());
                    return new Detail(verdict.resultOf(p.getId()), score.scores(), score.total());
                }));
    }

    /** 보관 시간 뒤 · 몰수패 — DB 의 승패만. */
    private static SessionResultResponse recordedOnly(FinishReason finishReason, List<DebateParticipant> participants,
                                                      Long userId) {
        return new SessionResultResponse(JudgeStatus.READY, finishReason, recordedWinner(participants), null,
                responses(participants, userId, p -> new Detail(p.getResult(), null, null)));
    }

    /**
     * DB 승패로 찾은 승자. 게스트가 이겼으면 게스트 쪽은 기록이 없으므로, 진 쪽이 아닌 참가자가 승자입니다.
     * 무승부거나 알 수 없으면 null 입니다.
     */
    private static Long recordedWinner(List<DebateParticipant> participants) {
        Optional<DebateParticipant> winner = participants.stream()
                .filter(p -> p.getResult() == DebateResult.WIN)
                .findFirst();
        if (winner.isPresent()) {
            return winner.get().getId();
        }
        boolean someoneLost = participants.stream().anyMatch(p -> p.getResult() == DebateResult.LOSE);
        if (!someoneLost) {
            return null;
        }
        return participants.stream()
                .filter(p -> p.getResult() == null)
                .map(DebateParticipant::getId)
                .findFirst()
                .orElse(null);
    }

    private static List<ParticipantResultResponse> responses(List<DebateParticipant> participants, Long userId,
                                                             Function<DebateParticipant, Detail> detailOf) {
        return participants.stream()
                .map(p -> {
                    Detail detail = detailOf.apply(p);
                    return new ParticipantResultResponse(p.getId(),
                            p.getUser() == null ? null : p.getUser().getNickname(),
                            null, p.getStance(), p.isUser(userId),
                            detail.result(), detail.scores(), detail.total());
                })
                .toList();
    }

    private record Detail(DebateResult result, Map<JudgeCriterion, Integer> scores, Integer total) {
    }
}
