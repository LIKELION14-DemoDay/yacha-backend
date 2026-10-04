package likelion.yacha_backend.domain.topic.dto;

import java.util.Arrays;
import java.util.List;
import likelion.yacha_backend.domain.topic.entity.Category;
import likelion.yacha_backend.domain.topic.entity.Subcategory;

/**
 * 카테고리와 하위 카테고리 ({@code GET /categories}).
 *
 * @param code          요청에 쓰는 값 (예: {@code TECH_AND_FUTURE})
 * @param name          화면에 보여 줄 이름 (예: 기술과 미래)
 * @param subcategories 하위 카테고리
 */
public record CategoryResponse(
        Category code,
        String name,
        List<SubcategoryResponse> subcategories
) {

    public record SubcategoryResponse(Subcategory code, String name) {
    }

    /** 카테고리 8개를 자동 제안 순환 순서(선언 순서)대로. */
    public static List<CategoryResponse> all() {
        return Arrays.stream(Category.values())
                .map(category -> new CategoryResponse(category, category.getDisplayName(),
                        Subcategory.of(category).stream()
                                .map(sub -> new SubcategoryResponse(sub, sub.getDisplayName()))
                                .toList()))
                .toList();
    }
}
