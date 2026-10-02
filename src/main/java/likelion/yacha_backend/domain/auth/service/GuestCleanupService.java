package likelion.yacha_backend.domain.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import likelion.yacha_backend.domain.auth.repository.RefreshTokenStore;
import likelion.yacha_backend.domain.session.repository.DebateParticipantRepository;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오래된 게스트 계정 정리
 *
 * 게스트는 비밀번호가 없어서 리프레시 토큰이 다시 들어올 유일한 수단
 * 토큰이 없으면(14일 동안 재발급 없음 · 로그아웃) 그 계정으로는 다시 들어올 수 없음
 * 비회원 게임 결과는 저장하지 않을 것이기 때문에 남겨 둘 데이터도 없음
 */
@Service
@RequiredArgsConstructor
public class GuestCleanupService {

    private final UserRepository userRepository;
    private final DebateParticipantRepository participantRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final GuestCleanupProperties properties;

    /**
     * 후보를 한 묶음 읽어 정리하고, 다음 묶음을 읽을 위치를 돌려줌
     *
     * 리프레시 토큰이 남은 후보는 아직 쓰는 계정이라 건너뜀
     * 건너뛴 후보를 다시 읽지 않도록 id 커서({@code afterId})로 넘김
     */
    @Transactional
    public Batch cleanUpBatch(LocalDateTime createdBefore, long afterId) {
        List<Long> candidates = userRepository.findGuestIdsCreatedBefore(
                createdBefore, afterId, Limit.of(properties.batchSize()));
        if (candidates.isEmpty()) {
            return new Batch(0, 0, afterId);
        }

        List<Long> stale = candidates.stream()
                .filter(userId -> refreshTokenStore.find(userId).isEmpty())
                .toList();
        List<Long> deletable = stale.isEmpty() ? List.of() : lockStillGuests(stale);
        if (!deletable.isEmpty()) {
            // 참가 기록이 users를 참조하므로 연결을 먼저 끊어야 지울 수 있음
            participantRepository.detachUsers(deletable);
            userRepository.deleteAllByIdInBatch(deletable);
        }
        return new Batch(candidates.size(), deletable.size(), candidates.get(candidates.size() - 1));
    }

    /**
     * 지우기 직전에 아직 게스트인지 다시 확인하고, 남은 행을 잠금
     *
     * 후보를 고른 뒤 지우기 전에 회원으로 승격할 수 있음 (로그아웃 직후 남은 액세스 토큰으로 /auth/upgrade)
     * 그 계정을 지우면 방금 가입한 회원이 사라짐
     *   이미 승격이 끝났으면 → is_guest가 false라 여기서 빠짐
     *   승격이 아직이면 → 행을 잠가 두었으므로 승격은 이 트랜잭션이 끝날 때까지 기다리고, 그 뒤 실패함
     */
    private List<Long> lockStillGuests(List<Long> userIds) {
        return userRepository.findGuestsByIdForUpdate(userIds).stream()
                .map(User::getId)
                .toList();
    }

    /**
     * @param scanned       읽은 후보 수. 묶음 크기보다 작으면 마지막 묶음
     * @param deleted       지운 계정 수
     * @param lastScannedId 다음 묶음은 이 id 다음부터
     */
    public record Batch(int scanned, int deleted, long lastScannedId) {
    }
}
