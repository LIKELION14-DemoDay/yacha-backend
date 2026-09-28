package likelion.yacha_backend.domain.topic.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 토론 주제 카테고리 8개. 방을 만들 때 고르고, 주제도 이 값으로 묶습니다. */
@Getter
@RequiredArgsConstructor
public enum Category {

    HUMAN("인간"),
    RELATIONSHIP("관계"),
    ETHICS("윤리"),
    SOCIETY("사회"),
    LIFE_AND_DEATH("삶과 죽음"),
    FUTURE_TECH("미래기술"),
    MONEY_AND_SUCCESS("돈과 성공"),
    TRUTH_AND_LIE("진실과 거짓"),
    ;

    /** 화면에 보여 줄 이름. */
    private final String displayName;
}
