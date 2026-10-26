package likelion.yacha_backend.domain.user.service;

import java.time.Clock;
import java.time.LocalDateTime;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.session.service.BattleRecordService;
import likelion.yacha_backend.domain.session.service.BattleRecordService.BattleRecord;
import likelion.yacha_backend.domain.user.dto.MyInfoResponse;
import likelion.yacha_backend.domain.user.dto.MyInfoResponse.StatsResponse;
import likelion.yacha_backend.domain.user.dto.NicknameUpdateRequest;
import likelion.yacha_backend.domain.user.entity.PhilosopherType;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final BattleRecordService battleRecordService;
    private final Clock clock;

    /** 게스트도 호출 가능 (게스트는 email이 null로 내려감) */
    public MyInfoResponse getMyInfo(Long userId) {
        return MyInfoResponse.from(findById(userId), stats(userId));
    }

    @Transactional
    public MyInfoResponse changeNickname(Long userId, NicknameUpdateRequest request) {
        User user = findById(userId);

        // 변경 감지: 트랜잭션이 끝날 때 Hibernate가 UPDATE를 만듦
        user.changeNickname(request.nickname());

        return MyInfoResponse.from(user, stats(userId));
    }

    /** 전적은 세션 도메인의 승패 기록입니다. 다른 도메인의 데이터는 repository 가 아니라 service 로 가져오는 것이 팀 규칙입니다. */
    private StatsResponse stats(Long userId) {
        BattleRecord record = battleRecordService.recordOf(userId);
        return new StatsResponse(record.wins(), record.losses());
    }

    /** 밸런스 게임 결과 저장. 다시 하면 덮어씀 */
    @Transactional
    public void completeBalanceGame(Long userId, PhilosopherType philosopherType) {
        findById(userId).completeBalanceGame(philosopherType);
    }

    /**
     * 밸런스 게임 건너뛰기. 이미 결과가 있으면 그대로 둠
     * 엔티티를 읽어 바꾸면 그 사이 커밋된 결과 제출을 낡은 값으로 덮어쓸 수 있어 조건부 UPDATE 로 처리함
     * 0 건이면 이미 결과가 있거나(그대로 둠) 사용자가 없음(USER_NOT_FOUND)
     */
    @Transactional
    public void skipBalanceGame(Long userId) {
        if (userRepository.skipBalanceGameIfNotDone(userId, LocalDateTime.now(clock)) == 0
                && !userRepository.existsById(userId)) {
            throw new BusinessException(AuthErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 토큰은 유효한데 사용자 행이 없는 경우 (탈퇴 등)
     * 인증 자체가 무의미해진 상황이라 인증 도메인의 에러 코드 사용
     */
    private User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
    }
}
