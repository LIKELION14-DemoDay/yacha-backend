package likelion.yacha_backend.domain.topic.entity;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 하위 카테고리 (명세 1-2). 주제의 소재이고, 값마다 상위 {@link Category} 가 정해져 있습니다.
 *
 * <p>주제를 뽑을 때는 카테고리 안에서 하위 카테고리를 먼저 랜덤으로 고른 뒤 그 안에서 주제를 고릅니다.
 * 하위 카테고리마다 주제 수가 달라도 고르게 나오게 하기 위해서입니다.
 */
@Getter
@RequiredArgsConstructor
public enum Subcategory {

    // 인간
    HUMAN_NATURE("인간 본성", Category.HUMAN),
    DESIRE("욕망", Category.HUMAN),
    HAPPINESS("행복", Category.HUMAN),
    SELF("자아", Category.HUMAN),
    FREE_WILL("자유의지", Category.HUMAN),

    // 관계
    LOVE("사랑", Category.RELATIONSHIP),
    DATING("연애", Category.RELATIONSHIP),
    FRIENDSHIP("우정", Category.RELATIONSHIP),
    FAMILY("가족", Category.RELATIONSHIP),
    BETRAYAL("배신", Category.RELATIONSHIP),
    TRUST("신뢰", Category.RELATIONSHIP),

    // 윤리
    GOOD_AND_EVIL("선악", Category.ETHICS),
    LYING("거짓말", Category.ETHICS),
    SACRIFICE("희생", Category.ETHICS),
    RESPONSIBILITY("책임", Category.ETHICS),
    RIGHT_AND_WRONG("옳고 그름", Category.ETHICS),

    // 사회
    FAIRNESS("공정", Category.SOCIETY),
    DISCRIMINATION("차별", Category.SOCIETY),
    RULES("규칙", Category.SOCIETY),
    CRIME("범죄", Category.SOCIETY),
    INDIVIDUAL_AND_COMMUNITY("개인과 공동체", Category.SOCIETY),

    // 삶과 죽음
    DEATH("죽음", Category.LIFE_AND_DEATH),
    IMMORTALITY("영생", Category.LIFE_AND_DEATH),
    MEANING_OF_LIFE("삶의 의미", Category.LIFE_AND_DEATH),
    EUTHANASIA("안락사", Category.LIFE_AND_DEATH),
    EXISTENCE("존재", Category.LIFE_AND_DEATH),

    // 기술과 미래
    AI("AI", Category.TECH_AND_FUTURE),
    ROBOT("로봇", Category.TECH_AND_FUTURE),
    VIRTUAL_REALITY("가상현실", Category.TECH_AND_FUTURE),
    CLONING("복제", Category.TECH_AND_FUTURE),
    HUMAN_AND_TECH("인간과 기술", Category.TECH_AND_FUTURE),

    // 돈과 성공
    WEALTH("부", Category.MONEY_AND_SUCCESS),
    LABOR("노동", Category.MONEY_AND_SUCCESS),
    SUCCESS("성공", Category.MONEY_AND_SUCCESS),
    MERITOCRACY("능력주의", Category.MONEY_AND_SUCCESS),
    HAPPINESS_AND_MONEY("행복과 돈", Category.MONEY_AND_SUCCESS),

    // 진실과 믿음
    TRUTH("진실", Category.TRUTH_AND_BELIEF),
    FALSEHOOD("거짓", Category.TRUTH_AND_BELIEF),
    RELIGION("종교", Category.TRUTH_AND_BELIEF),
    KNOWLEDGE("지식", Category.TRUTH_AND_BELIEF),
    REALITY("현실", Category.TRUTH_AND_BELIEF),
    BELIEF("믿음", Category.TRUTH_AND_BELIEF),
    ;

    /** 화면에 보여 줄 이름. */
    private final String displayName;
    private final Category category;

    /** 이 카테고리의 하위 카테고리를 선언 순서대로. */
    public static List<Subcategory> of(Category category) {
        return Arrays.stream(values())
                .filter(subcategory -> subcategory.category == category)
                .toList();
    }
}
