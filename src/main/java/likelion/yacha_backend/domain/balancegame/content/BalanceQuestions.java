package likelion.yacha_backend.domain.balancegame.content;

import static likelion.yacha_backend.domain.user.entity.PhilosopherType.EPICTETUS;
import static likelion.yacha_backend.domain.user.entity.PhilosopherType.EPICURUS;
import static likelion.yacha_backend.domain.user.entity.PhilosopherType.HOBBES;
import static likelion.yacha_backend.domain.user.entity.PhilosopherType.KANT;
import static likelion.yacha_backend.domain.user.entity.PhilosopherType.MILL;
import static likelion.yacha_backend.domain.user.entity.PhilosopherType.NIETZSCHE;
import static likelion.yacha_backend.domain.user.entity.PhilosopherType.ROUSSEAU;
import static likelion.yacha_backend.domain.user.entity.PhilosopherType.SARTRE;

import java.util.List;
import likelion.yacha_backend.domain.balancegame.content.BalanceQuestion.Option;

/** 밸런스 게임 질문 12개와 배점 (노션 "밸런스게임 › 철학자 기준" 배점표). 문구를 바꾸면 여기만 고칩니다. */
public final class BalanceQuestions {

    public static final List<BalanceQuestion> ALL = List.of(
            new BalanceQuestion(1, "친한 친구가 범죄를 저질렀다는 걸 알았다.",
                    new Option("관계와 상관없이 신고한다", KANT, HOBBES),
                    new Option("상황과 관계를 고려해 내가 판단한다", SARTRE, ROUSSEAU)),
            new BalanceQuestion(2, "큰 재난을 막기 위해 개인의 자유를 제한해야 한다면?",
                    new Option("다수를 살릴 수 있다면 제한할 수 있다", MILL, HOBBES),
                    new Option("위기 상황이어도 개인의 자유를 쉽게 침해해선 안 된다", KANT, EPICTETUS)),
            new BalanceQuestion(3, "안정적인 직장과 불확실한 꿈 중 하나를 골라야 한다.",
                    new Option("불확실해도 하고 싶은 일을 한다", NIETZSCHE, SARTRE),
                    new Option("안정적이고 평온한 삶을 선택한다", EPICURUS, EPICTETUS)),
            new BalanceQuestion(4, "사회가 매우 혼란스러워졌다. 가장 먼저 필요한 것은?",
                    new Option("강한 규칙과 질서", HOBBES, KANT),
                    new Option("시민의 자율과 합의를 통한 해결", MILL, ROUSSEAU)),
            new BalanceQuestion(5, "누군가 공개적으로 나를 심하게 비판했다.",
                    new Option("내가 통제할 수 없는 평가에는 크게 흔들리지 않는다", EPICTETUS, EPICURUS),
                    new Option("직접 맞서서 내 생각과 가치를 증명한다", NIETZSCHE, SARTRE)),
            new BalanceQuestion(6, "사회의 빈부격차가 매우 커졌다.",
                    new Option("어느 정도 재분배해서 격차를 줄여야 한다", ROUSSEAU, MILL),
                    new Option("개인의 소유권과 정해진 제도를 우선해야 한다", HOBBES, KANT)),
            new BalanceQuestion(7, "내가 바꿀 수 없는 환경이 내 인생을 제한한다고 느껴진다.",
                    new Option("환경과 상관없이 나 자신을 새롭게 정의한다", SARTRE, NIETZSCHE),
                    new Option("조건을 받아들이고 내가 통제할 수 있는 것에 집중한다", EPICTETUS, EPICURUS)),
            new BalanceQuestion(8, "당신에게 '잘 산다'는 것에 더 가까운 것은?",
                    new Option("욕심을 줄이고 평온하게 만족하며 사는 것", EPICURUS, EPICTETUS),
                    new Option("더 나은 사회와 공동체를 만드는 데 기여하는 것", ROUSSEAU, MILL)),
            new BalanceQuestion(9, "큰 기회가 생겼지만 오래전 친구와 한 약속을 깨야 한다.",
                    new Option("기회를 포기하고 약속을 지킨다", KANT, ROUSSEAU),
                    new Option("내 삶의 가능성을 위해 약속을 깰 수도 있다", NIETZSCHE, SARTRE)),
            new BalanceQuestion(10, "인생의 중요한 선택을 할 때 무엇을 더 믿는가?",
                    new Option("객관적인 기준과 합리적인 판단", MILL, KANT),
                    new Option("내가 중요하다고 느끼는 가치와 선택", SARTRE, NIETZSCHE)),
            new BalanceQuestion(11, "사람들 사이에 심각한 갈등이 생겼다.",
                    new Option("경쟁하고 부딪쳐서 명확하게 결론을 낸다", HOBBES, NIETZSCHE),
                    new Option("타협해서 전체의 고통과 피해를 줄인다", EPICURUS, MILL)),
            new BalanceQuestion(12, "사람들이 불행해지는 가장 큰 원인은 어디에 있다고 보는가?",
                    new Option("욕망과 태도를 제대로 다루지 못하는 개인", EPICTETUS, EPICURUS),
                    new Option("잘못 만들어진 사회와 제도", ROUSSEAU, HOBBES))
    );

    private BalanceQuestions() {
    }
}
