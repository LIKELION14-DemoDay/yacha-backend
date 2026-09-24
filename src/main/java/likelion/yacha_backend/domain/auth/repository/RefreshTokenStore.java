package likelion.yacha_backend.domain.auth.repository;

import java.util.Optional;

/**
 * 리프레시 토큰 저장소.
 *
 * <p><b>왜 저장하는가</b> — JWT 는 발급하고 나면 만료 전까지 취소할 수 없습니다. 액세스 토큰은
 * 그대로 두고(무상태), 리프레시 토큰만 서버가 기억해서 <b>로그아웃 · 강제 차단</b>을 가능하게 합니다.
 * 재발급 때 저장된 값과 다르면 거부하면 되기 때문입니다.
 *
 * <p><b>사용자당 1개</b>입니다. 다른 기기에서 로그인하면 값이 덮어써져서 이전 기기는 다음 재발급
 * 때 로그아웃됩니다. 다기기 동시 로그인이 필요해지면 키에 기기 식별자를 더하는 식으로 확장합니다.
 *
 * <p>인터페이스로 둔 이유는 두 가지입니다.
 * <ul>
 *   <li>테스트에서 Redis 없이 인메모리 구현으로 갈아끼우기 위해 (CI 에 Redis 가 없습니다)</li>
 *   <li>나중에 저장소를 바꾸더라도 {@code AuthService} 는 그대로 두기 위해</li>
 * </ul>
 */
public interface RefreshTokenStore {

    /**
     * 리프레시 토큰을 저장합니다. 이미 있으면 덮어씁니다(재발급 시 교체 = rotation).
     *
     * <p>⚠️ Redis 는 DB 트랜잭션에 포함되지 않습니다. {@code @Transactional} 이 롤백돼도
     * 여기에 쓴 값은 되돌아가지 않으므로, <b>DB 작업이 모두 끝난 뒤 마지막에</b> 호출하세요.
     */
    void save(Long userId, String token);

    /** 저장된 토큰. 없거나 만료됐으면 비어 있습니다. */
    Optional<String> find(Long userId);

    /** 로그아웃 · 강제 차단 · 재사용 탐지 시 삭제합니다. 없는 키를 지워도 예외가 나지 않습니다. */
    void delete(Long userId);
}
