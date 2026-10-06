package likelion.yacha_backend.domain.topic.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 토론 주제 상위 카테고리 8개 (명세 1-2). 방을 만들 때 고르고, 주제는 {@link Subcategory} 로 묶입니다.
 *
 * <p>선언 순서가 자동 제안의 카테고리 순환 순서입니다 (명세 2-3).
 */
@Getter
@RequiredArgsConstructor
public enum Category {

    HUMAN("인간"),
    RELATIONSHIP("관계"),
    ETHICS("윤리"),
    SOCIETY("사회"),
    LIFE_AND_DEATH("삶과 죽음"),
    TECH_AND_FUTURE("기술과 미래"),
    MONEY_AND_SUCCESS("돈과 성공"),
    TRUTH_AND_BELIEF("진실과 믿음"),
    ;

    /** 화면에 보여 줄 이름. */
    private final String displayName;
}
