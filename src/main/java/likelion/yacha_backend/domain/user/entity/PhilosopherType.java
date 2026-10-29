package likelion.yacha_backend.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 철학 유형 — 밸런스 게임 결과 (명세 1-2 철학자 유형 8개). 프로필 이미지는 프론트가 이 코드로 고릅니다.
 * 결과 화면의 긴 설명은 밸런스 게임 도메인에 있습니다.
 *
 * <p>찬성 · 반대 문구는 매칭 → 채팅방 로딩 화면에서 참가자마다 자기 유형 말투로 보여 줍니다.
 * 어떤 예 / 아니오 질문에도 어울리도록 유형마다 고정입니다.
 */
@Getter
@RequiredArgsConstructor
public enum PhilosopherType {

    KANT("칸트", "원칙을 배신하지 않는 자", "원칙에 비추어 옳소", "보편 법칙이 될 수 없소"),
    HOBBES("홉스", "질서를 세우는 자", "질서를 위해 따르겠소", "만인의 투쟁이 시작되겠군"),
    SARTRE("사르트르", "선택으로 자신을 만드는 자", "내가 선택하겠소", "그 선택은 거부하겠소"),
    ROUSSEAU("루소", "사회를 의심하는 자", "모두의 뜻에 맞소", "사회가 사람을 망치는군"),
    MILL("밀", "최대 행복을 계산하는 자", "더 큰 행복을 주겠군", "고통이 더 크겠소"),
    EPICTETUS("에픽테토스", "흔들리지 않는 자", "담담히 받아들이겠소", "흔들릴 이유가 없소"),
    NIETZSCHE("니체", "기존의 답을 부수는 자", "그 길을 긍정하겠소", "낡은 답은 부수겠소"),
    EPICURUS("에피쿠로스", "평온을 지키는 자", "마음이 평온해지겠군", "평온을 해칠 뿐이오"),
    ;

    /** 유형이 없을 때(밸런스 게임을 안 했거나 건너뜀) 화면에 보여 줄 이름. */
    public static final String NONE_NAME = "일반인";

    /** 유형이 없을 때의 찬성 문구. */
    public static final String NONE_AGREE_TEXT = "찬성해요";

    /** 유형이 없을 때의 반대 문구. */
    public static final String NONE_DISAGREE_TEXT = "반대해요";

    /** 화면 이름. */
    private final String displayName;

    /** 결과 화면 · 마이페이지의 한 줄 별칭. */
    private final String alias;

    /** 로딩 화면의 찬성 문구. */
    private final String agreeText;

    /** 로딩 화면의 반대 문구. */
    private final String disagreeText;
}
