package likelion.yacha_backend.domain.user.service;

import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.user.dto.MyInfoResponse;
import likelion.yacha_backend.domain.user.dto.MyInfoResponse.StatsResponse;
import likelion.yacha_backend.domain.user.dto.NicknameUpdateRequest;
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

    /** 게스트도 호출 가능 (게스트는 email이 null로 내려감) */
    public MyInfoResponse getMyInfo(Long userId) {
        // TODO: 판정 도메인(debate_result)이 생기면 그 도메인의 service 를 통해 실제 전적을 채웁니다.
        //       다른 도메인의 데이터는 repository 가 아니라 service 로 가져오는 것이 팀 규칙입니다.
        return MyInfoResponse.from(findById(userId), StatsResponse.empty());
    }

    @Transactional
    public MyInfoResponse changeNickname(Long userId, NicknameUpdateRequest request) {
        User user = findById(userId);

        // 변경 감지: 트랜잭션이 끝날 때 Hibernate가 UPDATE를 만듦
        user.changeNickname(request.nickname());

        return MyInfoResponse.from(user, StatsResponse.empty());
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
