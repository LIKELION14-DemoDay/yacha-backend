package likelion.yacha_backend.domain.auth.repository;

import java.time.Duration;

/**
 * 같은 IP가 짧은 시간에 너무 많이 부르는 것을 막는 곳
 *
 * 용도마다 따로 세고, 처음 부른 때부터 {@code window} 동안 횟수를 센 뒤 지나면 0부터 다시 셈
 * 한도와 구간은 쓰는 쪽이 자기 설정에서 넘김 (이메일 중복 확인 · 비밀번호 찾기 등)
 */
public interface IpRateLimiter {

    /**
     * 이번 호출을 세고 허용되는지 알려 줌
     *
     * @param purpose     무엇을 세는지. 용도가 다르면 서로 영향을 주지 않음 (예: {@code email-check})
     * @param maxRequests 한 IP가 {@code window} 안에 부를 수 있는 횟수
     * @param window      횟수를 세는 구간. 1ms 이상이어야 함 (설정 쪽에서 검증)
     * @return 이번 호출까지 {@code maxRequests}번 이하면 true
     */
    boolean tryAcquire(String purpose, String clientIp, int maxRequests, Duration window);
}
