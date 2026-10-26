package likelion.yacha_backend.domain.session.service;

import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.session.repository.WinLossCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전투 기록 — 마이페이지의 승리 · 패배 수 (명세 2-1 {@code GET /users/me}).
 *
 * <p>DB 에 남는 게임 결과는 참가자별 {@code result} 뿐이라 그것을 셉니다. <b>무승부({@code DRAW})는 세지 않습니다</b>
 * (마이페이지에 승리 · 패배만 보여줌). 승패가 없는 판(무효 · 판정 실패 · 진행 중)과 게스트 때 한 게임은 {@code result} 가
 * 없거나 가입 때 참가 기록에서 끊겨 자연히 빠집니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleRecordService {

    private final DebateParticipantRepository participantRepository;

    public record BattleRecord(long wins, long losses) {
    }

    /** 승리 · 패배 수. 쿼리 한 번으로 셉니다. */
    public BattleRecord recordOf(Long userId) {
        WinLossCount count = participantRepository.countWinsAndLosses(userId);
        return new BattleRecord(count.wins(), count.losses());
    }
}
