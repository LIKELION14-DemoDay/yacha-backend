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

    /**
     * AI 프롬프트에 넣는 입장 설명. 입장 문구가 "예" · "아니오" 한 단어면 "~하지 않아도 되는가?" 같은 부정형 질문에서
     * 어느 쪽인지 헷갈리므로, 질문과 답을 한 문장으로 묶습니다.
     */
    public String stanceDescription(Stance stance) {
        return "질문 \"" + statement + "\"에 '" + stanceText(stance) + "'라고 답하는 입장";
    }
}
