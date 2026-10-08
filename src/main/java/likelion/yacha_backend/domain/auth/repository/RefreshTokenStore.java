package likelion.yacha_backend.domain.auth.repository;

import java.util.Optional;

/**
 * 리프레시 토큰 저장소
 * 사용자당 1개
 * 다른 기기에서 로그인하면 값이 덮어써져서 이전 기기는 다음 재발급 때 로그아웃
 * 다기기 동시 로그인이 필요해지면 키에 기기 식별자를 더하는 식으로 확장
 */
public interface RefreshTokenStore {

    void save(Long userId, String token);

    /** 저장된 토큰. 없거나 만료됐으면 비어 있음 */
    Optional<String> find(Long userId);

    /**
     * 저장된 토큰이 {@code expected}일 때만 {@code next}로 바꾸고 만료도 새로 잡음 (compare-and-set)
     *
     * 확인과 저장을 따로 하면 그 사이에 들어온 로그아웃 · 비밀번호 변경을 덮어쓸 수 있어서
     * 재발급은 이 메서드로 한 번에 바꿈
     *
     * @return 바꿨으면 true. 저장된 값이 없거나 다르면 아무것도 하지 않고 false
     */
    boolean replace(Long userId, String expected, String next);

    /** 로그아웃 · 강제 차단 · 재사용 탐지 시 삭제 */
    void delete(Long userId);
}
