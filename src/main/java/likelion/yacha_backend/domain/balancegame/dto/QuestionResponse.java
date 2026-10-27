package likelion.yacha_backend.domain.balancegame.dto;

import likelion.yacha_backend.domain.balancegame.content.BalanceQuestion;

/**
 * 질문 하나. 배점은 내려 주지 않습니다.
 *
 * @param number  1 부터 (화면의 "Q. 1 / 12")
 * @param optionA 왼쪽 카드
 * @param optionB 오른쪽 카드
 */
public record QuestionResponse(int number, String question, String optionA, String optionB) {

    public static QuestionResponse from(BalanceQuestion question) {
        return new QuestionResponse(question.number(), question.question(), question.a().text(), question.b().text());
    }
}
