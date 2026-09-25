package likelion.yacha_backend.domain.auth.service;

import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 유스케이스
 * 게스트 발급·회원가입·로그인·재발급·로그아웃·승격 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final TokenIssuer tokenIssuer;
    private final GuestNicknameGenerator nicknameGenerator;

    /**
     * 게스트 계정 만들고 토큰 발급
     */
    @Transactional
    public IssuedTokens createGuest() {
        User guest = userRepository.save(User.createGuest(nicknameGenerator.generate()));

        // 토큰 발급(= 저장소 기록)은 DB 작업이 끝난 뒤 마지막에
        IssuedTokens tokens = tokenIssuer.issue(guest);

        log.info("게스트 생성: userId={}", guest.getId());
        return tokens;
    }
}
