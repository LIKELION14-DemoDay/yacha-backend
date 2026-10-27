package likelion.yacha_backend.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 철학 유형 — 밸런스 게임 결과 (명세 1-2 철학자 유형 8개). 프로필 이미지는 프론트가 이 코드로 고릅니다.
 * 결과 화면의 긴 설명은 밸런스 게임 도메인에 있습니다.
 */
@Getter
@RequiredArgsConstructor
public enum PhilosopherType {

    KANT("칸트", "원칙을 배신하지 않는 자"),
    HOBBES("홉스", "질서를 세우는 자"),
    SARTRE("사르트르", "선택으로 자신을 만드는 자"),
    ROUSSEAU("루소", "사회를 의심하는 자"),
    MILL("밀", "최대 행복을 계산하는 자"),
    EPICTETUS("에픽테토스", "흔들리지 않는 자"),
    NIETZSCHE("니체", "기존의 답을 부수는 자"),
    EPICURUS("에피쿠로스", "평온을 지키는 자"),
    ;

    /** 유형이 없을 때(밸런스 게임을 안 했거나 건너뜀) 화면에 보여 줄 이름. */
    public static final String NONE_NAME = "일반인";

    /** 화면 이름. */
    private final String displayName;

    /** 결과 화면 · 마이페이지의 한 줄 별칭. */
    private final String alias;
}
