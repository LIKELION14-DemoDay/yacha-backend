package likelion.yacha_backend.domain.auth.service;

import java.util.Locale;
import likelion.yacha_backend.domain.auth.repository.EmailCheckLimiter;
import likelion.yacha_backend.domain.user.repository.UserRepository;
import likelion.yacha_backend.global.exception.BusinessException;
import likelion.yacha_backend.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 이메일 중복 확인
 *
 * 가입 화면은 원래 이미 있는 이메일을 알려주는 곳이라 결과 자체는 공개해도 됨
 * 다만 이메일 목록을 대량으로 대조하는 데 쓰이지 않게 IP마다 호출 수를 제한함
 *
 * 안내용이라 최종 판단은 가입(/auth/signup · /auth/upgrade) 때 다시 함
 * 확인한 뒤 가입하기 전 사이에 다른 사람이 같은 이메일로 가입할 수 있기 때문
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAvailabilityService {

    private final EmailCheckLimiter emailCheckLimiter;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean isAvailable(String email, String clientIp) {
        // 제한을 먼저 확인해서, 막힌 요청은 DB까지 가지 않음
        if (!emailCheckLimiter.tryAcquire(clientIp)) {
            log.warn("이메일 중복 확인 호출 제한 초과: ip={}", clientIp);
            throw new BusinessException(GlobalErrorCode.TOO_MANY_REQUESTS);
        }
        // 가입과 같은 정규화. 대소문자만 다른 이메일도 같은 이메일로 봄
        return !userRepository.existsByEmail(email.trim().toLowerCase(Locale.ROOT));
    }
}
