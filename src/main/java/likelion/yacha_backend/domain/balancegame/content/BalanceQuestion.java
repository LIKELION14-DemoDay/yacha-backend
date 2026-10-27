package likelion.yacha_backend.domain.balancegame.content;

import likelion.yacha_backend.domain.user.entity.PhilosopherType;

/**
 * 밸런스 게임 질문 하나. 선택지마다 철학자 두 명에게 점수를 줍니다 (주 +2, 부 +1).
 *
 * @param number 1 부터
 */
public record BalanceQuestion(int number, String question, Option a, Option b) {

    /** 주 점수 · 부 점수. 철학자마다 +2 를 세 번, +1 을 세 번 받도록 짜여 있습니다 (최대 9점). */
    public static final int PRIMARY_SCORE = 2;
    public static final int SECONDARY_SCORE = 1;

    public Option option(Choice choice) {
        return choice == Choice.A ? a : b;
    }

    /**
     * @param primary   +2 를 받는 철학자
     * @param secondary +1 을 받는 철학자
     */
    public record Option(String text, PhilosopherType primary, PhilosopherType secondary) {
    }
}
