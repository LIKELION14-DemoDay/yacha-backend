package likelion.yacha_backend.domain.ai;

import java.util.Objects;
import likelion.yacha_backend.domain.session.entity.Stance;

/**
 * AI 에 넘기는 주제. 엔티티({@code Topic})를 그대로 넘기지 않아 AI 구현이 JPA 에 묶이지 않습니다.
 *
 * @param statement    동의 / 비동의로 답하는 질문형 명제
 * @param agreeText    찬성 입장 문구
 * @param disagreeText 반대 입장 문구
 */
public record AiTopic(String statement, String agreeText, String disagreeText) {

    public AiTopic {
        Objects.requireNonNull(statement, "statement");
        Objects.requireNonNull(agreeText, "agreeText");
        Objects.requireNonNull(disagreeText, "disagreeText");
    }

    /** 입장에 맞는 문구. */
    public String stanceText(Stance stance) {
        return stance == Stance.AGREE ? agreeText : disagreeText;
    }
}
