package likelion.yacha_backend.domain.auth.repository;

import java.util.Optional;

/**
 * 비밀번호 재설정에 필요한 짧은 수명 상태를 보관
 *
 * 두 가지를 담음
 *   재설정 토큰 — 메일 링크에 실리는 값. 30분 뒤 사라지고, 한 번 쓰면 없어짐
 *   재요청 제한 — 같은 이메일로 메일을 연속 보내지 못하게 막음
 *
 * 토큰은 JWT가 아님.
 * 서버만 알면 되는 값이라 서명이 필요 없고, 메일·URL 에 들어가므로 짧을수록 좋음
 * 대신 저장소에 있는 동안만 유효하므로 즉시 폐기가 가능
 */
public interface PasswordResetStore {

    /** 토큰 → userId 로 저장. 같은 사용자의 이전 토큰은 무효가 됨(사용자당 하나). 유효시간이 지나면 사라짐 */
    void save(String token, Long userId);

    /**
     * 토큰에 해당하는 userId를 꺼내고 즉시 삭제(1회용)
     *
     * 조회와 삭제가 하나의 동작이어야 함
     * 나눠서 하면 같은 링크를 동시에 두 번 눌렀을 때 둘 다 통과할 수 있음
     */
    Optional<Long> consume(String token);

    /**
     * 이 이메일로 지금 메일을 보내도 되는지 확인하고, 된다면 다음 요청을 제한
     * 제한이 없으면 남의 메일함에 재설정 메일을 계속 보낼 수 있음
     */
    boolean tryAcquireSendSlot(String email);
}
