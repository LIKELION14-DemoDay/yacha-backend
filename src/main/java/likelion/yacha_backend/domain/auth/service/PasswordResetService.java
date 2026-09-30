package likelion.yacha_backend.domain.auth.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import likelion.yacha_backend.domain.auth.dto.PasswordResetConfirmRequest;
import likelion.yacha_backend.domain.auth.dto.PasswordResetRequest;
import likelion.yacha_backend.domain.auth.exception.AuthErrorCode;
import likelion.yacha_backend.domain.auth.mail.MailProperties;
import likelion.yacha_backend.domain.auth.mail.MailSender;
import likelion.yacha_backend.domain.auth.repository.PasswordResetStore;
import likelion.yacha_backend.domain.user.entity.User;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호를 잊었을 때의 재설정
 * 로그인 화면의 비밀번호 찾기 흐름
 *
 * 로그인한 상태에서 바꾸는 {@code AuthService.changePassword}와 다름
 * 본인 확인을 메일로 보낸 링크로 대신함
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final PasswordResetStore passwordResetStore;
    private final MailSender mailSender;
    private final MailProperties mailProperties;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    /**
     * 재설정 메일을 보냄
     *
     * 가입 여부와 관계없이 아무 값도 돌려주지 않음
     * 호출한 쪽은 항상 성공 응답을 내보냄
     * 여기서 "그런 계정 없음"을 알려주면 가입된 이메일 목록을 수집할 수 있음
     *
     * 트랜잭션을 걸지 않음
     * 조회만 하고, 메일 발송이 트랜잭션 안에 들어가면 안 됨
     */
    public void sendResetMail(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());

        if (!passwordResetStore.tryAcquireSendSlot(email)) {
            // 최근에 이미 보냄 : 조용히 끝냄 — 응답은 성공한 경우와 같아야 함
            log.info("비밀번호 재설정 메일 재요청이 제한됐습니다.");
            return;
        }

        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getPassword() == null) {
                // 소셜로만 가입한 계정
                // 바꿀 비밀번호가 없으므로 링크 대신 안내를 보냄
                mailSender.sendPasswordResetForSocialAccount(email, user.getProvider());
                return;
            }

            String token = generateToken();
            passwordResetStore.save(token, user.getId());
            mailSender.sendPasswordReset(email, mailProperties.passwordResetUrl(token));
            log.info("비밀번호 재설정 메일 발송: userId={}", user.getId());
        });
    }

    /**
     * 링크로 받은 토큰을 확인하고 새 비밀번호를 저장
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
