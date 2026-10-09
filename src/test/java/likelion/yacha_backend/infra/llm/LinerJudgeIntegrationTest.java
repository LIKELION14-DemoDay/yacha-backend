
package likelion.yacha_backend.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import likelion.yacha_backend.domain.ai.AiMessage;
import likelion.yacha_backend.domain.ai.AiTopic;
import likelion.yacha_backend.domain.ai.JudgeCriterion;
import likelion.yacha_backend.domain.ai.JudgeRequest;
import likelion.yacha_backend.domain.ai.JudgeResult;
import likelion.yacha_backend.domain.session.entity.Stance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@DisplayName("Liner AI 판정 실제 API 연동 테스트")
@EnabledIfEnvironmentVariable(
        named = "LINER_REAL_API_TEST",
        matches = "true"
)
class LinerJudgeIntegrationTest {

    @Test
    @DisplayName("실제 Liner API로 두 참가자의 토론을 판정한다")
    void judgeRealDebate() {

        String apiKey = System.getenv("LINER_API_KEY");

        assertThat(apiKey)
                .as("LINER_API_KEY 환경변수가 필요합니다.")
                .isNotBlank();

        LinerJudge judge = new LinerJudge(
                apiKey,
                "liner-mark"
        );

        AiTopic topic = new AiTopic(
                "AI가 인간의 일자리를 대체하는 것은 바람직한가?",
                "AI가 인간의 일자리를 대체하는 것은 바람직하다",
                "AI가 인간의 일자리를 대체하는 것은 바람직하지 않다"
        );

        JudgeRequest request = new JudgeRequest(
                topic,
                List.of(
                        new JudgeRequest.Participant(
                                101L,
                                Stance.AGREE
                        ),
                        new JudgeRequest.Participant(
                                202L,
                                Stance.DISAGREE
                        )
                ),
                List.of(
                        new AiMessage(
                                101L,
                                AiMessage.Kind.ARGUMENT,
                                "AI는 생산성을 높이고 반복 업무를 자동화합니다. " +
                                        "이를 통해 사람은 창의적인 업무에 집중할 수 있습니다."
                        ),
                        new AiMessage(
                                202L,
                                AiMessage.Kind.ARGUMENT,
                                "AI의 일자리 대체는 실업과 소득 격차를 심화시킬 수 있습니다. " +
                                        "특히 재교육 기회가 부족한 노동자가 피해를 입습니다."
                        ),
                        new AiMessage(
                                101L,
                                AiMessage.Kind.REBUTTAL,
                                "기술 발전은 새로운 직업을 만들어 왔습니다. " +
                                        "정부와 기업이 재교육을 지원하면 변화에 대응할 수 있습니다."
                        ),
                        new AiMessage(
                                202L,
                                AiMessage.Kind.REBUTTAL,
                                "새로운 직업이 생기더라도 기존 노동자가 " +
                                        "즉시 전환할 수 있는 것은 아닙니다. " +
                                        "기술 도입 속도에 비해 사회적 지원은 부족할 수 있습니다."
                        ),
                        new AiMessage(
                                101L,
                                AiMessage.Kind.CHAT,
                                "재교육 지원을 확대하면 해결할 수 있지 않을까요?"
                        ),
                        new AiMessage(
                                202L,
                                AiMessage.Kind.CHAT,
                                "지원이 확대되기 전까지 발생하는 실업 문제는 어떻게 해결하나요?"
                        )
                )
        );

        long start = System.nanoTime();

        JudgeResult result = judge.judge(request);

        long elapsedMillis =
                (System.nanoTime() - start) / 1_000_000;

        // 두 참가자 모두 판정 결과가 있어야 한다.
        assertThat(result.scores()).hasSize(2);

        // 참가자별 네 가지 기준 점수가 모두 있어야 한다.
        for (Long participantId : List.of(101L, 202L)) {

            JudgeResult.ParticipantScore score =
                    result.scoreOf(participantId);

            assertThat(score.scores())
                    .containsOnlyKeys(JudgeCriterion.values());

            for (JudgeCriterion criterion
                    : JudgeCriterion.values()) {

                assertThat(score.scores().get(criterion))
                        .isBetween(0, 25);
            }

            assertThat(score.total())
                    .isBetween(0, 100);
        }

        // 기준별 판정 요약 4개가 있어야 한다.
        assertThat(result.summaries()).hasSize(4);

        for (JudgeCriterion criterion
                : JudgeCriterion.values()) {

            String summary = result.summaries().get(criterion);

            assertThat(summary)
                    .isNotBlank()
                    .doesNotContain("\n", "\r");
        }

        System.out.println("=== 실제 Liner AI 판정 ===");

        for (JudgeResult.ParticipantScore score
                : result.scores()) {

            System.out.println(
                    "참가자 ID: " + score.participantId()
            );

            for (JudgeCriterion criterion
                    : JudgeCriterion.values()) {

                System.out.println(
                        "  " + criterion + ": "
                                + score.scores().get(criterion)
                );
            }

            System.out.println(
                    "  총점: " + score.total()
            );
        }

        System.out.println("=== 기준별 판정 요약 ===");

        for (JudgeCriterion criterion
                : JudgeCriterion.values()) {

            System.out.println(
                    criterion + ": "
                            + result.summaries().get(criterion)
            );
        }

        System.out.println(
                "응답 시간: " + elapsedMillis + "ms"
        );
    }
}
