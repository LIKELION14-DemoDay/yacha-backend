package likelion.yacha_backend.domain.philosophy.dto;

import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;
import likelion.yacha_backend.domain.topic.entity.Topic;

/**
 * 1분 철학 글의 주제. 화면의 해시태그 · 질문
 *
 * @param topicId         주제 id
 * @param category        카테고리 코드 (예: {@code ETHICS})
 * @param categoryName    카테고리 이름 (예: 윤리)
 * @param subcategory     하위 카테고리 코드 (예: {@code LYING})
 * @param subcategoryName 하위 카테고리 이름 (예: 거짓말)
 * @param statement       질문 (예: 행복을 위해 거짓말을 해도 될까?)
 */
public record PhilosophyTopicResponse(
        Long topicId,
        Category category,
        String categoryName,
        Subcategory subcategory,
        String subcategoryName,
        String statement
) {

    public static PhilosophyTopicResponse from(Topic topic) {
        return new PhilosophyTopicResponse(topic.getId(),
                topic.getCategory(), topic.getCategory().getDisplayName(),
                topic.getSubcategory(), topic.getSubcategory().getDisplayName(),
                topic.getStatement());
    }
}
