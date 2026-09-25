package likelion.yacha_backend.domain.auth.service;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.dto.LoginRequest;
import likelion.yacha_backend.domain.auth.dto.SignupRequest;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    /** 존재하지 않는 이메일로 로그인을 시도했을 때 대조할 가짜 해시 */
    private String dummyPasswordHash;

    @PostConstruct
    void initDummyHash() {
        dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** 게스트 계정 만들고 토큰 발급 */
    @Transactional
    public IssuedTokens createGuest() {
        User guest = userRepository.save(User.createGuest(nicknameGenerator.generate()));

        // 토큰 발급은 DB 작업이 끝난 뒤 마지막에
        IssuedTokens tokens = tokenIssuer.issue(guest);

        log.info("게스트 생성: userId={}", guest.getId());
        return tokens;
    }

    /**
     * 회원가입.
     * 가입과 동시에 토큰을 발급(자동 로그인)
     */
    @Transactional
    public IssuedTokens signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        // 1차 방어: 사용자에게 친절한 에러를 주기 위한 검사
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = saveMember(email, request);

        IssuedTokens tokens = tokenIssuer.issue(user);
        log.info("회원가입: userId={}", user.getId());
        return tokens;
    }

    /**
     * 로그인
     * 이메일·비밀번호를 대조하고 토큰을 발급
     */
    public IssuedTokens login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElse(null);

        // 사용자가 없어도 해시 대조를 한 번 수행
        if (user == null || user.getPassword() == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        // 저장된 해시에서 salt 를 꺼내 같은 조건으로 다시 해시해 비교
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        return tokenIssuer.issue(user);
    }

    /** 이메일을 소문자로 */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 2차 방어: DB 의 UNIQUE 제약 */
    private User saveMember(String email, SignupRequest request) {
        try {
            return userRepository.saveAndFlush(User.createMember(
                    email,
                    passwordEncoder.encode(request.password()),
                    request.nickname()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS, e);
        }
    }
}
