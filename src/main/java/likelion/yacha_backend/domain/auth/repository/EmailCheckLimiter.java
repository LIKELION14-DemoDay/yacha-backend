package likelion.yacha_backend.domain.auth.repository;

/**
 * 이메일 중복 확인을 IP마다 몇 번 불렀는지 세는 곳
 *
 * 처음 부른 때부터 {@link EmailCheckProperties#window()} 동안 횟수를 세고, 지나면 0부터 다시 셈
 */
public interface EmailCheckLimiter {

    /**
     * 이번 호출을 세고 허용되는지 알려 줌
     *
     * @return 이번 호출까지 {@link EmailCheckProperties#maxRequests()}번 이하면 true
     */
    boolean tryAcquire(String clientIp);
}
