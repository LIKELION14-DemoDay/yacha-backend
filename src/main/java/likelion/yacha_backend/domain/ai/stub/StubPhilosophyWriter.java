package likelion.yacha_backend.domain.ai.stub;

import java.util.List;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.PhilosopherWriting;
import likelion.yacha_backend.domain.ai.PhilosopherWriting.Section;
import likelion.yacha_backend.domain.ai.PhilosophyWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 임시 1분 철학 글. 주제와 관계없이 같은 철학자 두 명의 고정 글을 돌려줌
 * {@code ai.mode=stub}(기본값)일 때 등록됨. 로컬 · 테스트에서 Liner 없이 생성 · 검토 · 공개 흐름을 확인하는 용도
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubPhilosophyWriter implements PhilosophyWriter {

    static final List<PhilosopherWriting> WRITINGS = List.of(
            new PhilosopherWriting("아리스토텔레스", List.of(
                    new Section("중용에서 답을 찾다",
                            "아리스토텔레스는 좋은 삶이란 지나침과 모자람 사이의 알맞은 상태, 곧 중용을 지키는 데 있다고 보았습니다. "
                                    + "용기는 무모함과 비겁함 사이에 있고, 너그러움은 낭비와 인색함 사이에 있습니다. "
                                    + "그래서 하나의 규칙을 모든 상황에 똑같이 적용하기보다, 지금 이 상황에서 무엇이 알맞은지를 살피는 판단력을 중요하게 여겼습니다."),
                    new Section("습관이 사람을 만든다",
                            "그는 덕이 타고나는 것이 아니라 반복해서 행동하며 몸에 익히는 습관이라고 말했습니다. "
                                    + "정직한 행동을 거듭하면 정직한 사람이 되고, 작은 거짓을 거듭하면 그것이 성품이 됩니다. "
                                    + "따라서 한 번의 선택도 그 사람이 어떤 사람이 되어 가는지와 떨어져 있지 않다고 보았습니다.")
            )),
            new PhilosopherWriting("칸트", List.of(
                    new Section("결과보다 원칙",
                            "칸트는 행동이 옳은지 그른지는 결과가 아니라 그 행동을 이끈 원칙에 달려 있다고 보았습니다. "
                                    + "내가 따르는 원칙이 모든 사람이 따르는 보편 법칙이 되어도 괜찮은지를 물어보라고 했습니다. "
                                    + "모두가 필요할 때마다 그 원칙을 따른다면 원칙 자체가 무너지는 경우, 그 행동은 옳지 않다는 것입니다."),
                    new Section("사람을 수단으로 대하지 않기",
                            "또한 칸트는 사람을 언제나 목적으로 대하고 단지 수단으로만 대하지 말라고 했습니다. "
                                    + "상대의 이성과 선택을 존중하지 않고 내 목적을 위해 이용한다면, 좋은 의도라도 상대의 존엄을 해친다고 보았습니다. "
                                    + "그래서 그는 상황이 어렵더라도 지켜야 할 의무가 있다고 강조했습니다.")
            ))
    );

    @Override
    public List<PhilosopherWriting> write(AiTopic topic) {
        return WRITINGS;
    }
}
