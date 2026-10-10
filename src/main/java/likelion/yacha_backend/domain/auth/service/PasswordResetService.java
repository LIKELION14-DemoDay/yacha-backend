package likelion.yacha_backend.domain.auth.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import likelion.yacha_backend.domain.auth.dto.PasswordResetConfirmRequest;
import likelion.yacha_backend.domain.auth.dto.PasswordResetRequest;
import likelion.yacha_backend.domain.auth.dto.PasswordResetVerifyRequest;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.auth.repository.PasswordResetProperties;
import likelion.yacha_backend.domain.auth.mail.PasswordResetMailer;
import likelion.yacha_backend.domain.auth.repository.PasswordResetStore;
import likelion.yacha_backend.domain.auth.repository.PasswordResetStore.CodeCheck;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호를 잊었을 때의 재설정
 * 로그인 화면의 비밀번호 찾기 흐름
 *
 * 로그인한 상태에서 바꾸는 {@code AuthService.changePassword}와 다름
 * 본인 확인을 메일로 보낸 인증번호로 대신함
 *
 *   ① sendResetMail  이메일 → 메일로 6자리 인증번호 (3분)
 *   ② verify         이메일 + 인증번호 → 재설정 토큰 (10분, 1회용)
 *   ③ reset          재설정 토큰 + 새 비밀번호
 *
 * 가입 여부를 드러내지 않는 것이 핵심
 * ①은 항상 성공 응답이고, ②는 가입된 이메일과 아닌 이메일이 같은 응답을 받아야 함
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final int CODE_BOUND = 1_000_000;   // 000000 ~ 999999

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final PasswordResetStore passwordResetStore;
    private final PasswordResetMailer passwordResetMailer;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    /**
     * 인증번호 메일을 보냄
     *
     * 가입 여부와 관계없이 아무 값도 돌려주지 않음
     * 호출한 쪽은 항상 성공 응답을 내보냄
     * 여기서 "그런 계정 없음"을 알려주면 가입된 이메일 목록을 수집할 수 있음
     *
     * 인증번호는 가입 여부와 관계없이 저장함 (가입 안 된 이메일 · 소셜 전용 계정은 메일만 안 보냄)
     * 저장하지 않으면 ②에서 "틀림"과 "만료" 응답이 갈려 가입 여부가 드러남
     *
     * 트랜잭션을 걸지 않음
     * 조회만 하고, 메일 발송이 트랜잭션 안에 들어가면 안 됨
     *
     * 메일은 {@link PasswordResetMailer}가 따로 보냄
     * 가입된 이메일도 발송을 기다리지 않고 바로 돌아오고, 발송이 실패해도 응답은 같음
     */
    public void sendResetMail(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());

        if (!passwordResetStore.tryAcquireSendSlot(email)) {
            // 최근에 이미 보냄 : 조용히 끝냄 — 응답은 성공한 경우와 같아야 함
            log.info("비밀번호 재설정 메일 재요청이 제한됐습니다.");
            return;
        }

        // 새로 저장하면 이전 인증번호와 틀린 횟수는 버려짐
        String code = generateCode();
        passwordResetStore.saveCode(email, code);

        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getPassword() == null) {
                // 소셜로만 가입한 계정
                // 바꿀 비밀번호가 없으므로 인증번호 대신 안내를 보냄
                passwordResetMailer.sendSocialAccountNotice(user.getId(), email, user.getProvider());
                return;
            }

            passwordResetMailer.sendResetCode(user.getId(), email, code);
            log.info("비밀번호 재설정 인증번호 발송 요청: userId={}", user.getId());
        });
    }

    /**
     * 인증번호를 확인하고 재설정 토큰을 발급
     *
     * 틀리면 INVALID_RESET_CODE, 인증번호가 없으면(만료 · 5번 틀림 · 요청 안 함) RESET_CODE_EXPIRED
     * 이메일이 하루 10번을 틀렸으면 RESET_ATTEMPTS_EXCEEDED (인증번호를 새로 받아 횟수를 되돌리는 대입을 막음)
     * 가입 안 된 이메일도 ①에서 인증번호를 저장해 두고 틀린 횟수도 똑같이 세므로 같은 응답이 나옴
     *
     * 트랜잭션을 걸지 않음. 조회만 함
     */
    public String verify(PasswordResetVerifyRequest request) {
        String email = normalizeEmail(request.email());

        CodeCheck result = passwordResetStore.checkCode(email, request.code());
        if (result == CodeCheck.LOCKED) {
            throw new BusinessException(AuthErrorCode.RESET_ATTEMPTS_EXCEEDED);
        }
        if (result == CodeCheck.EXPIRED) {
            throw new BusinessException(AuthErrorCode.RESET_CODE_EXPIRED);
        }
        if (result == CodeCheck.MISMATCHED) {
            throw new BusinessException(AuthErrorCode.INVALID_RESET_CODE);
        }

        // 메일을 보내지 않은 인증번호(가입 안 된 이메일 · 소셜 전용)를 맞힌 경우는 틀린 것과 같게 응답
        // 5번 안에 맞힐 확률이 0.0005%라 사실상 일어나지 않음
        User user = userRepository.findByEmail(email)
                .filter(found -> found.getPassword() != null)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_RESET_CODE));

        String token = generateToken();
        passwordResetStore.save(token, user.getId());
        log.info("비밀번호 재설정 인증 완료: userId={}", user.getId());
        return token;
    }

    /**
     * 인증으로 받은 토큰을 확인하고 새 비밀번호를 저장
     *
     * 토큰은 꺼내는 순간 사라짐
     *
     * 성공하면 저장된 리프레시 토큰 삭제
     * 여기서는 로그인한 상태가 아니므로 새 토큰을 발급하지 않고, 사용자는 새 비밀번호로 다시 로그인
     */
    @Transactional
    public void reset(PasswordResetConfirmRequest request) {
        Long userId = passwordResetStore.consume(request.token())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_RESET_TOKEN));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_RESET_TOKEN));

        if (user.getPassword() == null) {
            // 소셜 전용 계정에는 토큰을 발급하지 않지만, 그 사이 계정 상태가 바뀌었을 수 있음
            throw new BusinessException(AuthErrorCode.INVALID_RESET_TOKEN);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        tokenIssuer.revoke(userId);

        log.info("비밀번호 재설정 완료: userId={}", userId);
    }

    /** 6자리 숫자. 앞자리 0도 그대로 둠 (예: 012345). 추측할 수 없어야 하므로 {@link SecureRandom} 사용 */
    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
    }

    /** 추측할 수 없어야 하므로 {@link SecureRandom} 사용 */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** 대소문자가 달라도 같은 계정을 찾아야 함 */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
