package likelion.yacha_backend.domain.auth.repository;

import java.util.Optional;

/**
 * 비밀번호 재설정에 필요한 짧은 수명 상태를 보관
 *
 * 세 가지를 담음
 *   인증번호 — 메일로 보내는 6자리. 이메일당 하나, 3분 뒤 사라지고 정해진 횟수만 틀릴 수 있음
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

    /**
     * 이메일에 인증번호를 저장. 이메일당 하나라 이전 인증번호와 틀린 횟수는 버림
     * 유효시간({@link PasswordResetProperties#codeTtl()})이 지나면 사라짐
     */
    void saveCode(String email, String code);

    /**
     * 인증번호를 확인하고 시도 횟수를 하나 늘림
     *
     * 확인 · 횟수 증가 · 삭제가 하나의 동작이어야 함
     * 나눠서 하면 동시에 여러 번 보내 횟수 제한을 넘기거나, 같은 번호로 두 번 통과할 수 있음
     */
    CodeCheck checkCode(String email, String code);

    enum CodeCheck {
        /** 맞음. 인증번호는 지워져 다시 쓸 수 없음 */
        MATCHED,
        /** 틀림. 남은 횟수 안에서 다시 시도할 수 있음 */
        MISMATCHED,
        /** 인증번호가 없음. 요청한 적 없음 · 유효시간 지남 · 틀린 횟수 초과 · 이미 사용함 */
        EXPIRED
    }
}
