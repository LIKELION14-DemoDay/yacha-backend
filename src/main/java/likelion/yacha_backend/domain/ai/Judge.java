package likelion.yacha_backend.domain.ai;

/**
 * 판정 (명세 2-6). CHAT 구간이 끝나면(260초) 부릅니다. 몰수패({@code FORFEIT})는 판정하지 않습니다.
 *
 * <p>두 사람을 <b>한 번에</b> 채점합니다 — 결과 화면이 비교 막대라 같은 기준이어야 합니다.
 * 실패하면 예외를 던지세요. 재시도 · {@code FAILED} 처리는 부르는 쪽이 합니다.
 */
public interface Judge {

    /**
     * @return 두 참가자의 기준별 점수와 기준별 한 줄 요약
     */
    JudgeResult judge(JudgeRequest request);
}
