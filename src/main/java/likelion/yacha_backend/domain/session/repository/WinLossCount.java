package likelion.yacha_backend.domain.session.repository;

/**
 * 사용자 한 명의 승리 · 패배 수 — 전투 기록을 한 번의 쿼리로 셀 때 씁니다. 무승부 · 승패 없는 판은 세지 않습니다.
 */
public record WinLossCount(long wins, long losses) {
}
