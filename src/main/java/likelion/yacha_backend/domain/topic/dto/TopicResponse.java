package likelion.yacha_backend.domain.topic.dto;

import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;

/**
 * 주제 (명세 2-2).
 *
 * @param statement    주제 화면 · 대기 목록에 뜨는 질문
 * @param agreeText    찬성(예) 입장 문구
 * @param disagreeText 반대(아니오) 입장 문구
 */
public record TopicResponse(
        Long id,
        Category category,
        Subcategory subcategory,
        String statement,
        String agreeText,
        String disagreeText
) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getCategory(), topic.getSubcategory(),
                topic.getStatement(), topic.getAgreeText(), topic.getDisagreeText());
    }
}
