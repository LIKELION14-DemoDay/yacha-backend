package likelion.yacha_backend.domain.auth.service;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Map;
import likelion.yacha_backend.domain.auth.dto.IssuedTokens;
import likelion.yacha_backend.domain.auth.dto.LoginRequest;
import likelion.yacha_backend.domain.auth.dto.SignupRequest;
import likelion.yacha_backend.domain.auth.dto.SocialLoginRequest;
import likelion.yacha_backend.domain.auth.dto.UpgradeRequest;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.auth.social.SocialProfile;
import likelion.yacha_backend.domain.auth.social.SocialTokenVerifier;
import likelion.yacha_backend.domain.user.entity.Provider;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.security.jwt.JwtTokenProvider;
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
    private final JwtTokenProvider jwtTokenProvider;
    /** 설정된 소셜 공급자만 들어 있습니다. 없는 공급자로 로그인하면 UNSUPPORTED_PROVIDER. */
    private final Map<Provider, SocialTokenVerifier> socialTokenVerifiers;

    /** users.nickname 컬럼 길이와 맞춥니다. 소셜 닉네임이 더 길면 잘라서 저장합니다. */
    private static final int NICKNAME_MAX_LENGTH = 50;

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

    /**
     * 액세스 토큰 재발급
     *
     * <p>쿠키로 온 리프레시 토큰을 <b>세 단계</b>로 검사
     * <ol>
     *   <li>서명·만료 — 토큰 자체가 우리 서버가 발급한 것이고 아직 살아 있는가</li>
     *   <li>종류 — 리프레시 토큰인가 (액세스 토큰으로 재발급받지 못하게)</li>
     *   <li>저장소 — 지금 유효한 토큰인가</li>
     * </ol>
     */
    public IssuedTokens reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!jwtTokenProvider.validate(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = parseUserId(refreshToken);

        String saved = tokenIssuer.findStoredRefreshToken(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (!saved.equals(refreshToken)) {
            // 서명은 유효한데 저장된 값과 다름 = 이미 교체된(한번 쓴) 토큰
            //
            // 프론트가 재발급 요청을 동시에 여러 번 보내면 여기에 걸려 로그아웃됨
            // 재발급은 하나로 묶어서 보내야 함
            log.warn("이미 사용된 리프레시 토큰입니다. 저장된 토큰을 폐기합니다. userId={}", userId);
            tokenIssuer.revoke(userId);
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    // 토큰은 멀쩡한데 사용자가 없어서 남은 토큰 정리
                    tokenIssuer.revoke(userId);
                    return new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
                });

        // 액세스 토큰만이 아니라 리프레시 토큰도 새로 발급해 저장소 값을 바꿈
        // role을 여기서 DB로부터 다시 읽으므로, 권한 변경도 이 시점에 반영
        return tokenIssuer.issue(user);
    }

    /**
     * 카카오 · 구글 로그인
     *
     * 인증 방법만 다르고, 성공한 뒤는 이메일 로그인과 완전히 같음
     *
     * id_token 검증 → sub · 이메일 · 닉네임
     * {@code provider + sub} 로 기존 계정 조회
     * 없으면 새 계정. 단 같은 이메일의 기존 계정이 있으면 만들지 않고 409 (자동 연결하지 않음)
     */
    @Transactional
    public IssuedTokens socialLogin(SocialLoginRequest request) {
        SocialTokenVerifier verifier = socialTokenVerifiers.get(request.provider());
        if (verifier == null) {
            // LOCAL을 보냈거나, 서버에 그 공급자의 클라이언트 ID가 설정되지 않은 경우
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }

        SocialProfile profile = verifier.verify(request.idToken());

        User user = userRepository.findByProviderAndProviderId(profile.provider(), profile.providerId())
                .orElseGet(() -> createIfEmailFree(profile));

        return tokenIssuer.issue(user);
    }

    /**
     * 처음 보는 소셜 계정. 새 계정을 만듭니다.
     *
     * 같은 이메일의 기존 계정이 있으면 연결하지 않고 409 로 막습니다 (아래 주석 참고)
     */
    private User createIfEmailFree(SocialProfile profile) {
        String email = profile.hasVerifiedEmail() ? normalizeEmail(profile.email()) : null;

        if (email != null && userRepository.existsByEmail(email)) {
            // 같은 이메일의 기존 계정이 있어도 <b>자동으로 연결하지 않습니다.</b>
            //
            // 소셜 쪽 이메일은 확인됐지만, 우리 쪽 계정의 이메일은 확인된 적이 없습니다.
            // (가입할 때 메일 인증을 받지 않습니다) 그래서 자동으로 붙이면 이런 일이 가능합니다.
            //
            //   1. 공격자가 victim@example.com 으로 먼저 가입 (비밀번호는 공격자 것)
            //   2. 진짜 주인이 구글로 로그인 → 확인된 이메일이라 공격자의 계정에 연결됨
            //   3. 비밀번호는 그대로라 공격자도 계속 로그인 가능 = 계정을 나눠 쓰게 됨
            //
            // 계정 연결은 이메일 인증을 붙인 뒤, 또는 이미 로그인한 상태에서만 허용해야 합니다.
            // 그때까지는 원래 쓰던 방법으로 로그인하도록 안내합니다. (코드 리뷰 반영)
            throw new BusinessException(AuthErrorCode.SOCIAL_EMAIL_CONFLICT);
        }

        return createSocialUser(profile, email);
    }

    /**
     * 새 소셜 계정
     *
     * 확인되지 않은 이메일은 저장하지 않음
     */
    private User createSocialUser(SocialProfile profile, String verifiedEmail) {
        String nickname = resolveNickname(profile.nickname());
        try {
            return userRepository.saveAndFlush(User.createSocial(
                    profile.provider(), profile.providerId(), verifiedEmail, nickname));
        } catch (DataIntegrityViolationException e) {
            // 같은 소셜 계정으로 동시에 두 번 요청이 들어온 경우입니다. UNIQUE(provider, provider_id)가 막습니다.
            //
            // 여기서 기존 행을 찾아 "정상 반환" 하면 안 됩니다. 제약 위반이 난 시점에 트랜잭션이
            // rollback-only 로 표시되기 때문에, 정상으로 끝내려 해도 커밋 때
            // UnexpectedRollbackException(500)이 납니다. 그 전에 리프레시 토큰이 Redis 에
            // 저장되므로 DB 와 저장소가 어긋나기까지 합니다. (코드 리뷰 반영)
            //
            // 그래서 여기서는 예외로 끝내고, 프론트가 한 번 더 호출하면 그때는 기존 계정으로 로그인됩니다.
            throw new BusinessException(AuthErrorCode.SOCIAL_LOGIN_RETRY, e);
        }
    }

    /** 소셜 닉네임이 없거나(카카오 비동의) 너무 길면 우리 규칙에 맞춤 */
    private String resolveNickname(String socialNickname) {
        if (socialNickname == null || socialNickname.isBlank()) {
            return nicknameGenerator.generate();
        }
        return socialNickname.length() > NICKNAME_MAX_LENGTH
                ? socialNickname.substring(0, NICKNAME_MAX_LENGTH)
                : socialNickname;
    }

    /**
     * 로그아웃
     * 저장된 리프레시 토큰을 지웁니다
     * 최대 10분 뒤 만료되면서 차단되고, 그전에 재발급을 시도하면 저장소가 비어 있어 실패
     * 프론트도 로그아웃할 때 메모리의 액세스 토큰을 버려야 함
     */
    public void logout(Long userId) {
        tokenIssuer.revoke(userId);
        log.info("로그아웃: userId={}", userId);
    }

    /**
     * 게스트를 회원으로 승격
     * 새 행을 만들면 기록을 옮기는 작업이 필요
     */
    @Transactional
    public IssuedTokens upgrade(Long userId, UpgradeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        if (!user.isGuest()) {
            throw new BusinessException(AuthErrorCode.ALREADY_MEMBER);
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        user.upgradeToMember(email, passwordEncoder.encode(request.password()), request.nickname());

        // 변경 감지로 UPDATE 되지만, UNIQUE 위반은 flush 시점에 드러남
        // 지금 내보내지 않으면 커밋 시점에 터져 500이 됨
        flushOrThrowDuplicateEmail();

        // 게스트 → 회원으로 상태가 바뀌었으니 토큰을 새로 발급, 이전 리프레시 토큰은 무효
        IssuedTokens tokens = tokenIssuer.issue(user);
        log.info("회원 승격: userId={}", userId);
        return tokens;
    }

    /** 토큰의 subject 를 userId 로 바꿈 */
    private Long parseUserId(String refreshToken) {
        try {
            return jwtTokenProvider.getUserId(refreshToken);
        } catch (RuntimeException e) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
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

    /** 승격에서 쓰는 2차 방어 */
    private void flushOrThrowDuplicateEmail() {
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS, e);
        }
    }
}
